package com.example.art_course.modules.payment.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.dto.CreatePaymentRequest;
import com.example.art_course.modules.payment.dto.PaymentResponseDto;
import com.example.art_course.modules.payment.dto.PaymentStatusResponseDto;
import com.example.art_course.modules.payment.entity.MoneyLedger;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.entity.WebhookLog;
import com.example.art_course.modules.payment.gateway.PaymentGateway;
import com.example.art_course.modules.payment.gateway.PaymentGatewayFactory;
import com.example.art_course.modules.payment.gateway.PayOSPaymentGateway;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.payment.repository.WebhookLogRepository;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.entity.RefundItem;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.refund.repository.RefundItemRepository;
import com.example.art_course.enrollment.entity.EnrollmentId;
import com.example.art_course.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookLogRepository webhookLogRepository;
    private final MoneyLedgerRepository moneyLedgerRepository;
    private final SupplyRepository supplyRepository;
    private final PaymentGatewayFactory paymentGatewayFactory;
    private final EnrollmentGrantService enrollmentGrantService;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PlatformTransactionManager transactionManager;

    // Inject qua field để các test dựng PaymentService bằng constructor không phải đổi; null thì bỏ qua bước customize.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.art_course.customize_request.repository.CustomizeRequestRepository customizeRequestRepository;

    /**
     * Tạo yêu cầu thanh toán và lấy link URL cổng thanh toán (PAY-01).
     */
    @Transactional
    public PaymentResponseDto createPaymentUrl(UUID parentId, CreatePaymentRequest request) {
        if (!PayOSPaymentGateway.GATEWAY_CODE.equalsIgnoreCase(request.getGateway())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "UNSUPPORTED_GATEWAY", "Chỉ hỗ trợ thanh toán qua PAYOS");
        }
        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng: " + request.getOrderId()));

        if (!order.getParentId().equals(parentId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thanh toán đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATUS", "Đơn hàng không ở trạng thái chờ thanh toán");
        }

        if (order.getExpiredAt() != null && order.getExpiredAt().isBefore(Instant.now())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "ORDER_EXPIRED", "Đơn hàng đã hết hạn thanh toán");
        }

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (payment != null && (payment.getStatus() == PaymentStatus.HELD
                || payment.getStatus() == PaymentStatus.RELEASED || payment.getStatus() == PaymentStatus.REFUNDED)) {
            throw new AppError(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "Đơn hàng đã được thanh toán");
        }
        PaymentGateway gateway = paymentGatewayFactory.getGateway(PayOSPaymentGateway.GATEWAY_CODE);
        if (payment != null && payment.getGatewayOrderCode() != null && payment.getPaymentUrl() != null) {
            PaymentGateway.GatewayQueryResult query = gateway.queryTransaction(payment);
            if (query.paid()) {
                requireMatchingAmount(payment, query.amount());
                processSuccessfulPayment(payment, query.gatewayTransactionId(), payment.getGateway());
                return paymentResponse(payment);
            }
            // PAY-01: mất mạng/chưa rõ trạng thái vẫn giữ link cũ, chỉ đổi mã khi cổng xác nhận đã hủy.
            if (!query.cancelled()) {
                payment.setStatus(PaymentStatus.PENDING);
                return paymentResponse(paymentRepository.save(payment));
            }
        }
        if (payment == null) {
            List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
            BigDecimal totalCommission = BigDecimal.ZERO;
            BigDecimal totalDetailNet = BigDecimal.ZERO;
            BigDecimal courseRate = configService.getCommissionRateCourse();
            BigDecimal supplyRate = configService.getCommissionRateSupply();
            for (OrderDetail detail : details) {
                BigDecimal rate = "SUPPLY".equalsIgnoreCase(detail.getProductType()) ? supplyRate : courseRate;
                BigDecimal commission = detail.getSubTotal().multiply(rate).setScale(0, RoundingMode.HALF_UP);
                detail.setCommissionRate(rate);
                detail.setCommissionAmount(commission);
                detail.setNetAmount(detail.getSubTotal().subtract(commission));
                orderDetailRepository.save(detail);
                totalCommission = totalCommission.add(commission);
                totalDetailNet = totalDetailNet.add(detail.getNetAmount());
            }
            payment = Payment.builder().orderId(order.getId()).teacherId(order.getTeacherId())
                    .gateway(PayOSPaymentGateway.GATEWAY_CODE).amount(order.getTotalAmount())
                    .commissionAmount(totalCommission).netAmount(totalDetailNet.add(order.getShippingFee()))
                    .status(PaymentStatus.PENDING).build();
        }
        payment.setStatus(PaymentStatus.PENDING);
        payment.setGatewayTransactionId(null);
        payment = paymentRepository.saveAndFlush(payment);
        payment.setPaymentUrl(gateway.createPaymentUrl(order, payment, request.getReturnUrl()));
        Payment savedPayment = paymentRepository.save(payment);
        return paymentResponse(savedPayment);
    }

    private PaymentResponseDto paymentResponse(Payment savedPayment) {
        return PaymentResponseDto.builder()
                .paymentId(savedPayment.getId())
                .orderId(savedPayment.getOrderId())
                .gateway(savedPayment.getGateway())
                .amount(savedPayment.getAmount())
                .paymentUrl(savedPayment.getPaymentUrl())
                .status(savedPayment.getStatus())
                .build();
    }

    /**
     * Xử lý webhook từ cổng thanh toán (PAY-01, PAY-02, PAY-09, BR-11).
     */
    @Transactional
    public Map<String, Object> handleWebhook(String gatewayCode, Map<String, String> params) {
        // PAY-09: chỉ nhận webhook của cổng thật đang hỗ trợ, kể cả khi profile test có bean MOCK.
        if (!PayOSPaymentGateway.GATEWAY_CODE.equalsIgnoreCase(gatewayCode)) {
            throw new AppError(HttpStatus.BAD_REQUEST, "UNSUPPORTED_GATEWAY", "Chỉ nhận webhook PAYOS");
        }
        log.info("[WEBHOOK] Nhận webhook từ cổng {}", gatewayCode);
        PaymentGateway gateway = paymentGatewayFactory.getGateway(gatewayCode);
        PaymentGateway.GatewayCallbackResult callbackResult = gateway.verifyCallback(params);

        String payloadJson = toJson(params);

        WebhookLog webhookLog = WebhookLog.builder()
                .gateway(gatewayCode.toUpperCase())
                .payload(payloadJson)
                .signatureValid(callbackResult.validSignature())
                .build();

        // 1. Kiểm tra chữ ký hợp lệ
        if (!callbackResult.validSignature()) {
            webhookLog.setProcessedStatus("WARNING");
            webhookLog.setResponseMessage("Chữ ký không hợp lệ: " + callbackResult.message());
            webhookLogRepository.save(webhookLog);
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Chữ ký webhook không hợp lệ: " + callbackResult.message());
        }

        // PAY-09: chỉ đọc id trước khi khóa, tránh dùng entity cũ trong persistence context.
        UUID paymentId = resolvePaymentIdFromParams(params);
        if (paymentId == null && callbackResult.gatewayTransactionId() != null) {
            paymentId = paymentRepository.findIdByGatewayAndGatewayTransactionId(
                    gatewayCode.toUpperCase(), callbackResult.gatewayTransactionId()).orElse(null);
        }
        UUID orderId = paymentId == null ? null : paymentRepository.findOrderIdById(paymentId).orElse(null);
        Payment payment = null;
        if (orderId != null) {
            orderRepository.findByIdForUpdate(orderId).orElseThrow();
            payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        }

        if (payment == null) {
            // PayOS gửi webhook mẫu (orderCode không có thật) khi đăng ký URL; phải trả 200
            webhookLog.setProcessedStatus("IGNORED");
            webhookLog.setResponseMessage("PayOS: không có payment khớp orderCode");
            webhookLogRepository.save(webhookLog);
            return Map.of("status", "IGNORED", "message", "Không có giao dịch khớp");
        }

        if (!gatewayCode.equalsIgnoreCase(payment.getGateway())) {
            throw new AppError(HttpStatus.CONFLICT, "GATEWAY_MISMATCH", "Cổng webhook không khớp giao dịch");
        }
        // 3. Kiểm tra Idempotent (Nếu đã HELD hoặc RELEASED hoặc trùng gateway_transaction_id thì bỏ qua xử lý lặp)
        if (payment.getStatus() == PaymentStatus.HELD || payment.getStatus() == PaymentStatus.RELEASED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            webhookLog.setProcessedStatus("IGNORED");
            webhookLog.setResponseMessage("Idempotent: Giao dịch đã được thanh toán thành công trước đó (status=" + payment.getStatus() + ")");
            webhookLogRepository.save(webhookLog);
            return Map.of("RspCode", "02", "Message", "Order already confirmed", "status", "SUCCESS", "message", "Idempotent: Đã xử lý trước đó");
        }

        if (callbackResult.gatewayTransactionId() != null) {
            Optional<Payment> dupTx = paymentRepository.findByGatewayAndGatewayTransactionId(
                    gatewayCode.toUpperCase(), callbackResult.gatewayTransactionId()
            );
            if (dupTx.isPresent()) {
                webhookLog.setProcessedStatus("IGNORED");
                webhookLog.setResponseMessage("Idempotent: gateway_transaction_id đã tồn tại");
                webhookLogRepository.save(webhookLog);
                return Map.of("RspCode", "02", "Message", "Order already confirmed", "status", "SUCCESS", "message", "Idempotent: Đã xử lý trước đó");
            }
        }

        // 4. Kiểm tra kết quả giao dịch từ cổng
        if (!callbackResult.success()) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            webhookLog.setProcessedStatus("FAILED");
            webhookLog.setResponseMessage("Giao dịch thất bại từ cổng: " + callbackResult.message());
            webhookLogRepository.save(webhookLog);
            return Map.of("RspCode", "00", "Message", "Confirm Success", "status", "FAILED", "message", callbackResult.message());
        }

        // 5. Kiểm tra số tiền thanh toán (PAY-02)
        if (payment.getAmount().compareTo(callbackResult.amount()) != 0) {
            webhookLog.setProcessedStatus("WARNING");
            webhookLog.setResponseMessage(String.format("Sai lệch số tiền: Kỳ vọng %s nhưng nhận %s",
                    payment.getAmount(), callbackResult.amount()));
            webhookLogRepository.save(webhookLog);
            throw new AppError(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH",
                    String.format("Số tiền thanh toán không khớp đơn hàng (kỳ vọng %s, nhận %s)", payment.getAmount(), callbackResult.amount()));
        }

        // 6. Xử lý thanh toán thành công trong 1 @Transactional
        processSuccessfulPayment(payment, callbackResult.gatewayTransactionId(), gatewayCode);

        webhookLog.setProcessedStatus("SUCCESS");
        webhookLog.setResponseMessage("Xử lý webhook thanh toán thành công");
        webhookLogRepository.save(webhookLog);

        return Map.of("RspCode", "00", "Message", "Confirm Success", "status", "SUCCESS", "message", "Thanh toán thành công");
    }

    /**
     * Logic nghiệp vụ cập nhật đơn hàng, giữ tiền, chuyển kho và cấp quyền sau commit.
     */
    @Transactional
    public void processSuccessfulPayment(Payment payment, String gatewayTransactionId, String gatewayCode) {
        Order order = orderRepository.findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng của thanh toán"));
        PaymentStatus from = payment.getStatus() == PaymentStatus.FAILED ? PaymentStatus.FAILED : PaymentStatus.PENDING;
        if (paymentRepository.transition(payment.getId(), from, PaymentStatus.HELD) == 0) {
            return;
        }
        boolean late = order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED;
        if (!late) {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
        }
        payment.setStatus(PaymentStatus.HELD);
        payment.setGatewayTransactionId(gatewayTransactionId);
        payment.setPaidAt(Instant.now());
        paymentRepository.saveAndFlush(payment);

        List<OrderDetail> details = new ArrayList<>(orderDetailRepository.findByOrderId(order.getId()));
        details.sort(Comparator.comparing(d -> d.getSupplyId() == null ? "" : d.getSupplyId().toString()));
        if (late) {
            // PAY-08: tiền đến sau hủy/hết hạn chỉ tạo yêu cầu hoàn, không bán lại hàng hay cấp quyền học.
            recordHold(order, payment, gatewayCode);
            Refund refund = refundRepository.save(Refund.builder().paymentId(payment.getId()).orderId(order.getId())
                    .parentId(order.getParentId()).totalAmount(payment.getAmount()).bankInfo("AUTO_REFUND_GATEWAY")
                    .reason("Đã nhận tiền cho đơn đã hủy hoặc hết hạn").status("PENDING").build());
            for (OrderDetail detail : details) {
                refundItemRepository.save(RefundItem.builder().refundId(refund.getId()).orderDetailId(detail.getId())
                        .amount(detail.getSubTotal()).reason(refund.getReason()).build());
            }
            notificationService.sendNotificationAsync(order.getParentId(), "Thanh toán chờ hoàn tiền",
                    "Đơn đã hủy/hết hạn; khoản tiền nhận được đang chờ nhân viên hoàn lại", "REFUND_REQUESTED", refund.getId());
            return;
        }
        List<OrderDetail> courseDetails = new ArrayList<>();

        for (OrderDetail detail : details) {
            if ("COURSE".equalsIgnoreCase(detail.getProductType())) {
                detail.setDeliveryStatus(null);
                courseDetails.add(detail);
            } else if ("SUPPLY".equalsIgnoreCase(detail.getProductType())) {
                detail.setDeliveryStatus(DeliveryStatus.PENDING);
                if (detail.getSupplyId() != null) {
                    Supply supply = supplyRepository.findByIdForUpdate(detail.getSupplyId()).orElse(null);
                    if (supply != null) {
                        // Giữ chỗ chuyển thành đã bán: reserved_quantity giảm, sold_quantity tăng, stock_quantity giảm
                        // (hủy/hoàn đơn cộng lại stock_quantity, nên không trừ ở đây thì tồn khả dụng tăng ảo sau khi thanh toán)
                        int newReserved = Math.max(0, supply.getReservedQuantity() - detail.getQuantity());
                        int newSold = supply.getSoldQuantity() + detail.getQuantity();
                        supply.setReservedQuantity(newReserved);
                        supply.setSoldQuantity(newSold);
                        supply.setStockQuantity(Math.max(0, supply.getStockQuantity() - detail.getQuantity()));
                        supply.syncStatusWithStock();
                        supplyRepository.save(supply);
                    }
                }
            } else if ("CUSTOMIZE".equalsIgnoreCase(detail.getProductType()) && customizeRequestRepository != null) {
                // CUS-09: đã trả tiền thì yêu cầu chuyển FUNDED, giáo viên bắt đầu làm khóa
                customizeRequestRepository.findById(detail.getCustomizeRequestId()).ifPresent(r -> {
                    r.setStatus("FUNDED");
                    customizeRequestRepository.save(r);
                });
            }
            orderDetailRepository.save(detail);
        }

        recordHold(order, payment, gatewayCode);
        executePostPaymentSync(order, courseDetails);
    }

    private void recordHold(Order order, Payment payment, String gatewayCode) {
        moneyLedgerRepository.save(MoneyLedger.builder().teacherId(payment.getTeacherId()).entryType("HOLD")
                .amount(payment.getNetAmount()).paymentId(payment.getId())
                .note("Tạm giữ tiền đơn " + order.getId() + " qua " + gatewayCode).build());
    }

    private void executePostPaymentSync(Order order, List<OrderDetail> courseDetails) {
        Runnable postActions = () -> {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            for (OrderDetail detail : courseDetails) {
                if (detail.getChildId() != null && detail.getCourseId() != null) {
                    try {
                        // PAY-06: recheck under the order lock so a concurrent refund cannot be granted again.
                        transaction.executeWithoutResult(status -> repairEnrollment(detail.getId()));
                    } catch (RuntimeException ex) {
                        log.warn("[ENROLLMENT] Grant deferred for detail {}: {}", detail.getId(), ex.getMessage());
                    }
                }
            }
            notificationService.notifyOrderPaid(order.getId(), order.getParentId(), order.getTeacherId());
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    postActions.run();
                }
            });
        } else {
            postActions.run();
        }
    }

    /**
     * Truy vấn chủ động trạng thái giao dịch từ cổng (PAY-10).
     */
    @Transactional
    public PaymentStatusResponseDto queryPaymentStatus(UUID paymentId, UUID currentUserId, String userRole) {
        UUID orderId = paymentRepository.findOrderIdById(paymentId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch"));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();

        boolean isParent = order.getParentId().equals(currentUserId);
        boolean isTeacher = order.getTeacherId().equals(currentUserId);
        boolean isStaffOrAdmin = "STAFF".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole);

        if (!isParent && !isTeacher && !isStaffOrAdmin) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền truy vấn giao dịch này");
        }

        boolean cancelled = false;
        if (payment.getStatus() == PaymentStatus.PENDING) {
            PaymentGateway gateway = paymentGatewayFactory.getGateway(payment.getGateway());
            PaymentGateway.GatewayQueryResult queryResult = gateway.queryTransaction(payment);
            if (queryResult.paid()) {
                requireMatchingAmount(payment, queryResult.amount());
                processSuccessfulPayment(payment, queryResult.gatewayTransactionId(), payment.getGateway());
            } else {
                // Hủy ở cổng không đổi trạng thái: payment vẫn PENDING, đơn giữ chỗ tới khi hết hạn hoặc bị hủy
                cancelled = queryResult.cancelled();
            }
        }

        return PaymentStatusResponseDto.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .gatewayTransactionId(payment.getGatewayTransactionId())
                .paidAt(payment.getPaidAt())
                .cancelled(cancelled)
                .refundPending(payment.getStatus() == PaymentStatus.HELD
                        && (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED))
                .build();
    }

    /**
     * Id các thanh toán còn PENDING tạo trong 24 giờ qua, để job đối soát (PAY-11) xử lý từng cái.
     */
    @Transactional(readOnly = true)
    public List<UUID> findReconcilablePaymentIds() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return paymentRepository.findByStatus(PaymentStatus.PENDING).stream()
                .filter(payment -> payment.getCreatedAt().isAfter(since))
                .map(Payment::getId)
                .toList();
    }

    /**
     * Đối soát MỘT thanh toán trong một transaction riêng: bước nào lỗi thì rollback cả trạng thái thanh toán,
     * đơn, tồn kho và sổ cái, nên thử lại lần sau vẫn thấy PENDING. Job phải gọi qua bean (không gọi nội bộ)
     * thì @Transactional mới có hiệu lực. Trả true nếu đã đồng bộ được.
     */
    @Transactional
    public boolean reconcilePayment(UUID paymentId) {
        UUID orderId = paymentRepository.findOrderIdById(paymentId).orElse(null);
        if (orderId == null || orderRepository.findByIdForUpdate(orderId).isEmpty()) return false;
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }
        PaymentGateway gateway = paymentGatewayFactory.getGateway(payment.getGateway());
        PaymentGateway.GatewayQueryResult query = gateway.queryTransaction(payment);
        if (!query.paid()) {
            return false;
        }
        requireMatchingAmount(payment, query.amount());
        processSuccessfulPayment(payment, query.gatewayTransactionId(), payment.getGateway());
        log.info("[RECONCILE] Khớp thành công đơn hàng {}", payment.getOrderId());
        return true;
    }

    // PAY-06: dùng đơn đã lưu làm hàng đợi khôi phục; thất bại vẫn còn để thử ở lượt sau.
    @Transactional(readOnly = true)
    public List<UUID> findMissingEnrollmentIds() {
        return orderDetailRepository.findMissingEnrollmentIds();
    }

    @Transactional
    public boolean repairEnrollment(UUID detailId) {
        UUID orderId = orderDetailRepository.findOrderIdById(detailId).orElse(null);
        if (orderId == null) return false;
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PAID) return false;
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || (payment.getStatus() != PaymentStatus.HELD && payment.getStatus() != PaymentStatus.RELEASED)) return false;
        OrderDetail detail = orderDetailRepository.findById(detailId).orElse(null);
        if (detail == null || detail.getCourseId() == null || detail.getChildId() == null
                || detail.getDeliveryStatus() == DeliveryStatus.REFUNDED
                || enrollmentRepository.existsById(new EnrollmentId(detail.getChildId(), detail.getCourseId()))) return false;
        enrollmentGrantService.grantAccessWithRetry(detail.getChildId(), detail.getCourseId(), orderId);
        return enrollmentRepository.existsByIdChildIdAndIdCourseIdAndStatusIgnoreCase(detail.getChildId(), detail.getCourseId(), "active");
    }

    private void requireMatchingAmount(Payment payment, BigDecimal amount) {
        if (amount == null || payment.getAmount().compareTo(amount) != 0) {
            throw new AppError(HttpStatus.CONFLICT, "AMOUNT_MISMATCH", "Số tiền cổng thanh toán không khớp giao dịch");
        }
    }

    private UUID resolvePaymentIdFromParams(Map<String, String> params) {
        try {
            return paymentRepository.findIdByGatewayOrderCode(Long.valueOf(params.get(PayOSPaymentGateway.DATA_PREFIX + "orderCode")))
                    .orElse(null);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
              .append(escapeJson(entry.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
