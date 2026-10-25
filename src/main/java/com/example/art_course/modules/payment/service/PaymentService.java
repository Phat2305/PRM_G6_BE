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
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.repository.SupplyRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        Order order = orderRepository.findById(request.getOrderId())
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

        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalDetailNet = BigDecimal.ZERO;

        BigDecimal courseRate = configService.getCommissionRateCourse();
        BigDecimal supplyRate = configService.getCommissionRateSupply();

        for (OrderDetail detail : details) {
            // Customize là khóa học làm riêng nên tính như khóa học
            BigDecimal rate = "SUPPLY".equalsIgnoreCase(detail.getProductType()) ? supplyRate : courseRate;
            BigDecimal comm = detail.getSubTotal().multiply(rate).setScale(0, RoundingMode.HALF_UP);
            BigDecimal net = detail.getSubTotal().subtract(comm);

            detail.setCommissionRate(rate);
            detail.setCommissionAmount(comm);
            detail.setNetAmount(net);
            orderDetailRepository.save(detail);

            totalCommission = totalCommission.add(comm);
            totalDetailNet = totalDetailNet.add(net);
        }

        // FIN-12: Phí ship chuyển toàn bộ cho Teacher, tách khỏi cơ sở tính hoa hồng
        BigDecimal teacherNet = totalDetailNet.add(order.getShippingFee());

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseGet(() -> Payment.builder()
                        .orderId(order.getId())
                        .teacherId(order.getTeacherId())
                        .build());

        if (payment.getStatus() == PaymentStatus.HELD || payment.getStatus() == PaymentStatus.RELEASED) {
            throw new AppError(HttpStatus.BAD_REQUEST, "ORDER_ALREADY_PAID", "Đơn hàng đã được thanh toán");
        }

        payment.setGateway(request.getGateway().toUpperCase());
        payment.setAmount(order.getTotalAmount());
        payment.setCommissionAmount(totalCommission);
        payment.setNetAmount(teacherNet);
        payment.setStatus(PaymentStatus.PENDING);

        payment = paymentRepository.saveAndFlush(payment);

        PaymentGateway gateway = paymentGatewayFactory.getGateway(payment.getGateway());
        String url = gateway.createPaymentUrl(order, payment, request.getReturnUrl());
        payment.setPaymentUrl(url);

        Payment savedPayment = paymentRepository.save(payment);

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
        log.info("[WEBHOOK] Nhận webhook từ cổng {}: {}", gatewayCode, params);
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

        // 2. Tìm payment theo ID từ params (có khóa PESSIMISTIC_WRITE để chống 2 luồng webhook đồng thời)
        UUID paymentId = resolvePaymentIdFromParams(gatewayCode, params);
        Payment payment = null;
        if (paymentId != null) {
            payment = paymentRepository.findByIdForUpdate(paymentId)
                    .or(() -> paymentRepository.findById(paymentId))
                    .orElse(null);
        }

        if (payment == null && callbackResult.gatewayTransactionId() != null) {
            payment = paymentRepository.findByGatewayAndGatewayTransactionId(gatewayCode.toUpperCase(), callbackResult.gatewayTransactionId())
                    .orElse(null);
        }

        if (payment == null && PayOSPaymentGateway.GATEWAY_CODE.equalsIgnoreCase(gatewayCode)) {
            // PayOS gửi webhook mẫu (orderCode không có thật) khi đăng ký URL; phải trả 200
            webhookLog.setProcessedStatus("IGNORED");
            webhookLog.setResponseMessage("PayOS: không có payment khớp orderCode");
            webhookLogRepository.save(webhookLog);
            return Map.of("status", "IGNORED", "message", "Không có giao dịch khớp");
        }

        if (payment == null) {
            webhookLog.setProcessedStatus("FAILED");
            webhookLog.setResponseMessage("Không tìm thấy giao dịch thanh toán trong hệ thống");
            webhookLogRepository.save(webhookLog);
            throw new AppError(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch thanh toán");
        }

        // 3. Kiểm tra Idempotent (Nếu đã HELD hoặc RELEASED hoặc trùng gateway_transaction_id thì bỏ qua xử lý lặp)
        if (payment.getStatus() == PaymentStatus.HELD || payment.getStatus() == PaymentStatus.RELEASED) {
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
        // Webhook, hỏi trạng thái và job đối soát có thể cùng thấy PENDING: chỉ nơi giành được dòng này mới xử lý tiếp,
        // nếu không tồn kho, sổ cái HOLD sẽ bị ghi hai lần.
        if (paymentRepository.transition(payment.getId(), PaymentStatus.PENDING, PaymentStatus.HELD) == 0) {
            log.info("[PAYMENT] Bỏ qua, giao dịch {} đã được xử lý ở nơi khác", payment.getId());
            return;
        }

        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng của thanh toán"));

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        payment.setStatus(PaymentStatus.HELD);
        payment.setGatewayTransactionId(gatewayTransactionId);
        payment.setPaidAt(Instant.now());
        paymentRepository.saveAndFlush(payment);

        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
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

        // Ghi sổ cái money_ledger loại HOLD
        MoneyLedger ledger = MoneyLedger.builder()
                .teacherId(payment.getTeacherId())
                .entryType("HOLD")
                .amount(payment.getNetAmount())
                .paymentId(payment.getId())
                .note("Tạm giữ tiền thanh toán đơn hàng " + order.getId() + " qua cổng " + gatewayCode)
                .build();
        moneyLedgerRepository.save(ledger);

        // Sau commit: gọi cấp quyền học BE1 (có @Retryable) và gửi thông báo
        executePostPaymentSync(order, courseDetails);
    }

    private void executePostPaymentSync(Order order, List<OrderDetail> courseDetails) {
        Runnable postActions = () -> {
            for (OrderDetail detail : courseDetails) {
                if (detail.getChildId() != null && detail.getCourseId() != null) {
                    enrollmentGrantService.grantAccessWithRetry(detail.getChildId(), detail.getCourseId(), order.getId());
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
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch: " + paymentId));

        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));

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
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }
        PaymentGateway gateway = paymentGatewayFactory.getGateway(payment.getGateway());
        PaymentGateway.GatewayQueryResult query = gateway.queryTransaction(payment);
        if (!query.paid()) {
            return false;
        }
        processSuccessfulPayment(payment, query.gatewayTransactionId(), payment.getGateway());
        log.info("[RECONCILE] Khớp thành công đơn hàng {}", payment.getOrderId());
        return true;
    }

    private UUID resolvePaymentIdFromParams(String gatewayCode, Map<String, String> params) {
        String idStr = null;
        if ("VNPAY".equalsIgnoreCase(gatewayCode)) {
            idStr = params.get("vnp_TxnRef");
        } else if ("MOMO".equalsIgnoreCase(gatewayCode)) {
            idStr = params.get("orderId");
        } else if ("PAYOS".equalsIgnoreCase(gatewayCode)) {
            try {
                return paymentRepository.findByGatewayOrderCode(Long.valueOf(params.get(PayOSPaymentGateway.DATA_PREFIX + "orderCode")))
                        .map(Payment::getId).orElse(null);
            } catch (NumberFormatException ignored) {
                return null;
            }
        } else {
            idStr = params.get("paymentId");
            if (idStr == null) idStr = params.get("orderId");
        }

        if (idStr != null) {
            try {
                return UUID.fromString(idStr);
            } catch (Exception ignored) {}
        }
        return null;
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
