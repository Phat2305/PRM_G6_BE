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

    /**
     * Tạo yêu cầu thanh toán và lấy link URL cổng thanh toán (PAY-01).
     */
    @Transactional
    public PaymentResponseDto createPaymentUrl(UUID parentId, CreatePaymentRequest request) {
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
            BigDecimal rate = "COURSE".equalsIgnoreCase(detail.getProductType()) ? courseRate : supplyRate;
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

        WebhookLog webhookLog = WebhookLog.builder()
                .gateway(gatewayCode.toUpperCase())
                .payload(params.toString())
                .signatureValid(callbackResult.validSignature())
                .build();

        // 1. Kiểm tra chữ ký hợp lệ
        if (!callbackResult.validSignature()) {
            webhookLog.setProcessedStatus("WARNING");
            webhookLog.setResponseMessage("Chữ ký không hợp lệ: " + callbackResult.message());
            webhookLogRepository.save(webhookLog);
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Chữ ký webhook không hợp lệ: " + callbackResult.message());
        }

        // 2. Tìm payment theo ID từ params (VNPay: vnp_TxnRef; MoMo: orderId; Mock: paymentId)
        UUID paymentId = resolvePaymentIdFromParams(gatewayCode, params);
        Payment payment = null;
        if (paymentId != null) {
            payment = paymentRepository.findById(paymentId).orElse(null);
        }

        if (payment == null && callbackResult.gatewayTransactionId() != null) {
            payment = paymentRepository.findByGatewayAndGatewayTransactionId(gatewayCode.toUpperCase(), callbackResult.gatewayTransactionId())
                    .orElse(null);
        }

        if (payment == null) {
            webhookLog.setProcessedStatus("FAILED");
            webhookLog.setResponseMessage("Không tìm thấy giao dịch thanh toán trong hệ thống");
            webhookLogRepository.save(webhookLog);
            throw new AppError(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch thanh toán");
        }

        // 3. Kiểm tra Idempotent (Nếu đã HELD hoặc RELEASED thì bỏ qua xử lý lặp)
        if (payment.getStatus() == PaymentStatus.HELD || payment.getStatus() == PaymentStatus.RELEASED) {
            webhookLog.setProcessedStatus("IGNORED");
            webhookLog.setResponseMessage("Idempotent: Giao dịch đã được thanh toán thành công trước đó (status=" + payment.getStatus() + ")");
            webhookLogRepository.save(webhookLog);
            return Map.of("status", "SUCCESS", "message", "Idempotent: Đã xử lý trước đó");
        }

        // 4. Kiểm tra kết quả giao dịch từ cổng
        if (!callbackResult.success()) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            webhookLog.setProcessedStatus("FAILED");
            webhookLog.setResponseMessage("Giao dịch thất bại từ cổng: " + callbackResult.message());
            webhookLogRepository.save(webhookLog);
            return Map.of("status", "FAILED", "message", callbackResult.message());
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

        return Map.of("status", "SUCCESS", "message", "Thanh toán thành công");
    }

    /**
     * Logic nghiệp vụ cập nhật đơn hàng, giữ tiền, chuyển kho và cấp quyền sau commit.
     */
    @Transactional
    public void processSuccessfulPayment(Payment payment, String gatewayTransactionId, String gatewayCode) {
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng của thanh toán"));

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        payment.setStatus(PaymentStatus.HELD);
        payment.setGatewayTransactionId(gatewayTransactionId);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

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
                        // Giữ chỗ chuyển thành đã bán: reserved_quantity giảm, sold_quantity tăng
                        int newReserved = Math.max(0, supply.getReservedQuantity() - detail.getQuantity());
                        int newSold = supply.getSoldQuantity() + detail.getQuantity();
                        supply.setReservedQuantity(newReserved);
                        supply.setSoldQuantity(newSold);
                        supplyRepository.save(supply);
                    }
                }
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

        if (payment.getStatus() == PaymentStatus.PENDING) {
            PaymentGateway gateway = paymentGatewayFactory.getGateway(payment.getGateway());
            PaymentGateway.GatewayQueryResult queryResult = gateway.queryTransaction(payment);
            if (queryResult.paid()) {
                processSuccessfulPayment(payment, queryResult.gatewayTransactionId(), payment.getGateway());
            }
        }

        return PaymentStatusResponseDto.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .gatewayTransactionId(payment.getGatewayTransactionId())
                .paidAt(payment.getPaidAt())
                .build();
    }

    /**
     * Đối soát thanh toán hằng ngày (PAY-11).
     */
    public int reconcileDailyPayments() {
        log.info("[RECONCILE] Bắt đầu đối soát giao dịch cổng thanh toán...");
        List<Payment> pendingPayments = paymentRepository.findByStatus(PaymentStatus.PENDING);
        int reconciledCount = 0;

        for (Payment payment : pendingPayments) {
            // Chỉ kiểm tra các thanh toán trong vòng 24 giờ qua
            if (payment.getCreatedAt().isAfter(Instant.now().minus(24, ChronoUnit.HOURS))) {
                try {
                    PaymentGateway gateway = paymentGatewayFactory.getGateway(payment.getGateway());
                    PaymentGateway.GatewayQueryResult query = gateway.queryTransaction(payment);
                    if (query.paid()) {
                        processSuccessfulPayment(payment, query.gatewayTransactionId(), payment.getGateway());
                        reconciledCount++;
                        log.info("[RECONCILE] Khớp thành công đơn hàng {}", payment.getOrderId());
                    }
                } catch (Exception ex) {
                    log.warn("[RECONCILE] Lỗi đối soát cho payment {}: {}", payment.getId(), ex.getMessage());
                }
            }
        }
        log.info("[RECONCILE] Kết thúc đối soát. Số giao dịch được đồng bộ: {}", reconciledCount);
        return reconciledCount;
    }

    private UUID resolvePaymentIdFromParams(String gatewayCode, Map<String, String> params) {
        String idStr = null;
        if ("VNPAY".equalsIgnoreCase(gatewayCode)) {
            idStr = params.get("vnp_TxnRef");
        } else if ("MOMO".equalsIgnoreCase(gatewayCode)) {
            idStr = params.get("orderId");
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
}
