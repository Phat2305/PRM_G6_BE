package com.example.art_course.modules.payment;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.common.statemachine.StateMachineValidator;
import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.modules.fulfillment.dto.TeacherConfirmDeliveryRequest;
import com.example.art_course.modules.fulfillment.service.FulfillmentService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.gateway.PaymentGateway;
import com.example.art_course.modules.payment.gateway.PaymentGatewayFactory;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.payment.repository.WebhookLogRepository;
import com.example.art_course.modules.payment.service.EnrollmentGrantService;
import com.example.art_course.modules.payment.service.PaymentService;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.modules.refund.repository.RefundItemRepository;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PROMPT 4 — JUNIT 5 TEST SUITE (Payment, Fulfillment & Retries)")
public class PaymentFulfillmentTests {

    // =========================================================================
    // TEST 1: WEBHOOK GỌI LẶP (IDEMPOTENT - PAY-01, 09)
    // =========================================================================
    @Test
    @DisplayName("1. Webhook gọi lặp (Idempotent): đơn/payment đã HELD thì không xử lý lại, trả về thành công")
    void testWebhook_IdempotentDuplicateCall() {
        OrderRepository orderRepo = mock(OrderRepository.class);
        OrderDetailRepository orderDetailRepo = mock(OrderDetailRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        WebhookLogRepository webhookLogRepo = mock(WebhookLogRepository.class);
        MoneyLedgerRepository moneyLedgerRepo = mock(MoneyLedgerRepository.class);
        SupplyRepository supplyRepo = mock(SupplyRepository.class);
        PaymentGatewayFactory gatewayFactory = mock(PaymentGatewayFactory.class);
        EnrollmentGrantService grantService = mock(EnrollmentGrantService.class);
        NotificationService notifService = mock(NotificationService.class);
        ConfigService configService = mock(ConfigService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);

        when(gatewayFactory.getGateway("VNPAY")).thenReturn(gateway);

        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .teacherId(teacherId)
                .gateway("VNPAY")
                .amount(new BigDecimal("500000"))
                .status(PaymentStatus.HELD) // Đã thanh toán thành công trước đó
                .build();

        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));

        Map<String, String> params = Map.of(
                "vnp_TxnRef", paymentId.toString(),
                "vnp_Amount", "50000000",
                "vnp_ResponseCode", "00"
        );

        when(gateway.verifyCallback(params)).thenReturn(new PaymentGateway.GatewayCallbackResult(
                true, true, "TX_VNP_12345", new BigDecimal("500000"), "Success"
        ));

        PaymentService paymentService = new PaymentService(
                orderRepo, orderDetailRepo, paymentRepo, webhookLogRepo,
                moneyLedgerRepo, supplyRepo, gatewayFactory, grantService,
                notifService, configService
        );

        Map<String, Object> response = paymentService.handleWebhook("VNPAY", params);

        assertEquals("SUCCESS", response.get("status"), "Idempotent response status phải là SUCCESS");
        assertTrue(response.get("message").toString().contains("Idempotent"), "Message phản hồi phải báo là Idempotent");

        // Đảm bảo không ghi trùng sổ cái, không cấp lại quyền học, không sửa lại đơn hàng
        verify(moneyLedgerRepo, never()).save(any());
        verify(orderRepo, never()).save(any());
        verify(grantService, never()).grantAccessWithRetry(any(), any(), any());

        // Kiểm tra log ghi nhận trạng thái IGNORED
        verify(webhookLogRepo).save(argThat(log -> "IGNORED".equals(log.getProcessedStatus())));
    }

    // =========================================================================
    // TEST 2: WEBHOOK SAI CHỮ KÝ (SIGNATURE VERIFICATION - PAY-09)
    // =========================================================================
    @Test
    @DisplayName("2. Webhook sai chữ ký (Signature Verification): từ chối, ghi webhook_logs WARNING, đơn giữ nguyên")
    void testWebhook_InvalidSignature() {
        OrderRepository orderRepo = mock(OrderRepository.class);
        OrderDetailRepository orderDetailRepo = mock(OrderDetailRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        WebhookLogRepository webhookLogRepo = mock(WebhookLogRepository.class);
        MoneyLedgerRepository moneyLedgerRepo = mock(MoneyLedgerRepository.class);
        SupplyRepository supplyRepo = mock(SupplyRepository.class);
        PaymentGatewayFactory gatewayFactory = mock(PaymentGatewayFactory.class);
        EnrollmentGrantService grantService = mock(EnrollmentGrantService.class);
        NotificationService notifService = mock(NotificationService.class);
        ConfigService configService = mock(ConfigService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);

        when(gatewayFactory.getGateway("VNPAY")).thenReturn(gateway);

        Map<String, String> params = Map.of(
                "vnp_TxnRef", UUID.randomUUID().toString(),
                "vnp_SecureHash", "fake_invalid_hash"
        );

        // Gateway xác nhận chữ ký giả mạo
        when(gateway.verifyCallback(params)).thenReturn(new PaymentGateway.GatewayCallbackResult(
                false, false, null, BigDecimal.ZERO, "Chữ ký sai lệch"
        ));

        PaymentService paymentService = new PaymentService(
                orderRepo, orderDetailRepo, paymentRepo, webhookLogRepo,
                moneyLedgerRepo, supplyRepo, gatewayFactory, grantService,
                notifService, configService
        );

        AppError error = assertThrows(AppError.class, () -> paymentService.handleWebhook("VNPAY", params));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("INVALID_SIGNATURE", error.getErrorCode());

        // Kiểm tra ghi log WARNING với signatureValid = false
        verify(webhookLogRepo).save(argThat(log -> "WARNING".equals(log.getProcessedStatus()) && !log.isSignatureValid()));

        // Tuyệt đối không thay đổi đơn hàng hay thanh toán
        verify(orderRepo, never()).save(any());
        verify(paymentRepo, never()).save(any());
    }

    // =========================================================================
    // TEST 3: WEBHOOK SAI SỐ TIỀN (AMOUNT MISMATCH - PAY-02)
    // =========================================================================
    @Test
    @DisplayName("3. Webhook sai số tiền (Amount Mismatch): sai số tiền từ chối cập nhật đơn, ghi log cảnh báo")
    void testWebhook_AmountMismatch() {
        OrderRepository orderRepo = mock(OrderRepository.class);
        OrderDetailRepository orderDetailRepo = mock(OrderDetailRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        WebhookLogRepository webhookLogRepo = mock(WebhookLogRepository.class);
        MoneyLedgerRepository moneyLedgerRepo = mock(MoneyLedgerRepository.class);
        SupplyRepository supplyRepo = mock(SupplyRepository.class);
        PaymentGatewayFactory gatewayFactory = mock(PaymentGatewayFactory.class);
        EnrollmentGrantService grantService = mock(EnrollmentGrantService.class);
        NotificationService notifService = mock(NotificationService.class);
        ConfigService configService = mock(ConfigService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);

        when(gatewayFactory.getGateway("VNPAY")).thenReturn(gateway);

        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        // Đơn hàng trong hệ thống có giá 500.000 VND
        Payment payment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .amount(new BigDecimal("500000"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));

        Map<String, String> params = Map.of(
                "vnp_TxnRef", paymentId.toString(),
                "vnp_Amount", "30000000" // Cổng báo số tiền 300.000 VND (thiếu tiền)
        );

        // Chữ ký hợp lệ nhưng số tiền nhận được là 300.000 thay vì 500.000
        when(gateway.verifyCallback(params)).thenReturn(new PaymentGateway.GatewayCallbackResult(
                true, true, "TX_VNP_HACKED", new BigDecimal("300000"), "Success"
        ));

        PaymentService paymentService = new PaymentService(
                orderRepo, orderDetailRepo, paymentRepo, webhookLogRepo,
                moneyLedgerRepo, supplyRepo, gatewayFactory, grantService,
                notifService, configService
        );

        AppError error = assertThrows(AppError.class, () -> paymentService.handleWebhook("VNPAY", params));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("AMOUNT_MISMATCH", error.getErrorCode());

        // Kiểm tra log ghi nhận WARNING và mô tả sai lệch số tiền
        verify(webhookLogRepo).save(argThat(log ->
                "WARNING".equals(log.getProcessedStatus()) &&
                        log.getResponseMessage().contains("Sai lệch số tiền")
        ));

        // Đơn hàng và thanh toán giữ nguyên trạng thái PENDING
        verify(orderRepo, never()).save(any());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    // =========================================================================
    // TEST 4: BE1 CẤP QUYỀN LỖI RỒI THỬ LẠI (@RETRYABLE - PAY-03)
    // =========================================================================
    @Configuration
    @EnableRetry
    static class RetryTestConfig {
        static IEnrollmentContract mockEnrollment = mock(IEnrollmentContract.class);

        @Bean
        public IEnrollmentContract enrollmentContract() {
            return mockEnrollment;
        }

        @Bean
        public EnrollmentGrantService enrollmentGrantService(IEnrollmentContract enrollmentContract) {
            return new EnrollmentGrantService(enrollmentContract);
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = {PaymentFulfillmentTests.RetryTestConfig.class})
    static class SpringRetryTests {

        @org.springframework.beans.factory.annotation.Autowired
        private EnrollmentGrantService service;

        @Test
        @DisplayName("4. BE1 cấp quyền lỗi rồi thử lại: @Retryable thử lại 3 lần khi gặp exception")
        void testBE1_GrantAccessRetryable() {
            IEnrollmentContract mockContract = RetryTestConfig.mockEnrollment;
            reset(mockContract);

            UUID childId = UUID.randomUUID();
            UUID courseId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            // Lần 1 và 2 ném RuntimeException (mạng chập chờn / timeout), lần 3 thành công
            doThrow(new RuntimeException("BE1 network timeout"))
                    .doThrow(new RuntimeException("BE1 busy"))
                    .doNothing()
                    .when(mockContract).grantAccess(childId, courseId, orderId);

            // Chạy thử lại qua Spring Retry Proxy
            service.grantAccessWithRetry(childId, courseId, orderId);

            // Kiểm chứng: được gọi đủ 3 lần
            verify(mockContract, times(3)).grantAccess(childId, courseId, orderId);
        }
    }

    // =========================================================================
    // TEST 5: CHUYỂN TRẠNG THÁI GIAO HÀNG (DELIVERY STATUS STATE MACHINE - FUL-01..08)
    // =========================================================================
    @Test
    @DisplayName("5. Chuyển trạng thái giao hàng hợp lệ và không hợp lệ (DeliveryStatus)")
    void testDeliveryStatus_Transitions() {
        // --- CÁC BƯỚC HỢP LỆ ---
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.PENDING, DeliveryStatus.CONFIRMED));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.PENDING, DeliveryStatus.REJECTED));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.PENDING, DeliveryStatus.CANCELLED));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.CONFIRMED, DeliveryStatus.SHIPPING));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.CONFIRMED, DeliveryStatus.CANCELLED));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERY_FAILED));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.DELIVERED, DeliveryStatus.COMPLETED));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.DELIVERED, DeliveryStatus.REFUNDED));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.DELIVERY_FAILED, DeliveryStatus.SHIPPING));
        assertTrue(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.DELIVERY_FAILED, DeliveryStatus.REFUNDED));

        // --- CÁC BƯỚC KHÔNG HỢP LỆ (BỊ TỪ CHỐI) ---
        assertFalse(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.PENDING, DeliveryStatus.COMPLETED));
        assertFalse(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.PENDING, DeliveryStatus.SHIPPING));
        assertFalse(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.CONFIRMED, DeliveryStatus.COMPLETED));
        assertFalse(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.SHIPPING, DeliveryStatus.COMPLETED));
        assertFalse(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.COMPLETED, DeliveryStatus.SHIPPING));
        assertFalse(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.CANCELLED, DeliveryStatus.CONFIRMED));
        assertFalse(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.REJECTED, DeliveryStatus.CONFIRMED));
        assertFalse(StateMachineValidator.isValidDeliveryTransition(DeliveryStatus.REFUNDED, DeliveryStatus.SHIPPING));

        // Kiểm tra thực tế qua FulfillmentService từ chối bước nhảy trạng thái sai
        OrderDetailRepository detailRepo = mock(OrderDetailRepository.class);
        OrderRepository orderRepo = mock(OrderRepository.class);
        SupplyRepository supplyRepo = mock(SupplyRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        RefundItemRepository refundItemRepo = mock(RefundItemRepository.class);
        NotificationService notifService = mock(NotificationService.class);
        ConfigService configService = mock(ConfigService.class);

        FulfillmentService service = new FulfillmentService(
                detailRepo, orderRepo, supplyRepo, paymentRepo, refundRepo,
                refundItemRepo, notifService, configService
        );

        UUID detailId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();

        Order order = Order.builder().id(orderId).teacherId(teacherId).parentId(UUID.randomUUID()).build();
        OrderDetail detail = OrderDetail.builder()
                .id(detailId)
                .orderId(orderId)
                .supplyId(UUID.randomUUID())
                .deliveryStatus(DeliveryStatus.CONFIRMED) // Đang CONFIRMED
                .build();

        when(detailRepo.findById(detailId)).thenReturn(Optional.of(detail));
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        // Cố tình CONFIRM lại khi đã CONFIRMED -> Bị ném ngoại lệ INVALID_STATE_TRANSITION
        AppError error = assertThrows(AppError.class, () ->
                service.teacherConfirmOrReject(detailId, teacherId, new TeacherConfirmDeliveryRequest("CONFIRM", null))
        );
        assertEquals("INVALID_STATE_TRANSITION", error.getErrorCode());
    }

    // =========================================================================
    // TEST 6: JOB TỰ XÁC NHẬN CHẠY HAI LẦN KHÔNG TÁC DỤNG KÉP (FUL-05)
    // =========================================================================
    @Test
    @DisplayName("6. Job tự xác nhận chạy hai lần không tác dụng kép (Idempotent Job - FUL-05)")
    void testAutoConfirmJob_IdempotentRunTwice() {
        OrderDetailRepository detailRepo = mock(OrderDetailRepository.class);
        OrderRepository orderRepo = mock(OrderRepository.class);
        SupplyRepository supplyRepo = mock(SupplyRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        RefundItemRepository refundItemRepo = mock(RefundItemRepository.class);
        NotificationService notifService = mock(NotificationService.class);
        ConfigService configService = mock(ConfigService.class);

        when(configService.getAutoConfirmDeliveryDays()).thenReturn(7);

        UUID detailId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).parentId(UUID.randomUUID()).teacherId(UUID.randomUUID()).build();
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        OrderDetail detail = OrderDetail.builder()
                .id(detailId)
                .orderId(orderId)
                .supplyId(UUID.randomUUID())
                .deliveryStatus(DeliveryStatus.DELIVERED)
                .deliveredAt(Instant.now().minus(8, ChronoUnit.DAYS)) // Đã giao 8 ngày trước (> 7 ngày)
                .build();

        // Lần chạy 1: tìm thấy 1 đơn đủ điều kiện
        when(detailRepo.findByDeliveryStatusAndDeliveredAtBefore(eq(DeliveryStatus.DELIVERED), any()))
                .thenReturn(List.of(detail))
                .thenReturn(Collections.emptyList()); // Lần chạy 2: không còn đơn nào vì đã COMPLETED

        FulfillmentService service = new FulfillmentService(
                detailRepo, orderRepo, supplyRepo, paymentRepo, refundRepo,
                refundItemRepo, notifService, configService
        );

        // Chạy lần 1:
        int processedRun1 = service.autoConfirmDeliveredItems();
        assertEquals(1, processedRun1, "Lần 1 phải tự động xác nhận 1 đơn");
        assertEquals(DeliveryStatus.COMPLETED, detail.getDeliveryStatus());
        assertNotNull(detail.getConfirmedDeliveredAt());
        verify(detailRepo, times(1)).save(detail);

        // Chạy lần 2:
        int processedRun2 = service.autoConfirmDeliveredItems();
        assertEquals(0, processedRun2, "Lần 2 chạy lại không có tác dụng phụ (idempotent = 0)");
        verify(detailRepo, times(1)).save(detail); // Vẫn chỉ save 1 lần, không lặp lại
    }

    // =========================================================================
    // TEST 7: WEBHOOK GỌI LẶP ĐỒNG THỜI HAI LUỒNG CÙNG GATEWAY_TRANSACTION_ID (POSTGRESQL THẬT)
    // =========================================================================
    @Test
    @DisplayName("7. Webhook gọi lặp ĐỒNG THỜI 2 luồng cùng gateway_transaction_id trên PostgreSQL thật: chỉ ghi 1 HOLD vào sổ cái")
    void testWebhook_ConcurrentSameGatewayTransactionId_OnRealPostgres() throws Exception {
        var ctx = com.example.art_course.common.PostgresTestcontainerEnv.getContext();
        var jdbc = ctx.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var paymentService = ctx.getBean(PaymentService.class);
        var paymentRepo = ctx.getBean(PaymentRepository.class);
        var orderRepo = ctx.getBean(OrderRepository.class);

        UUID parentId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String sharedTxId = "MOCK_CONCURRENT_TX_" + UUID.randomUUID();

        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'PARENT', 'ACTIVE')",
                parentId, "parent_cw_" + parentId);
        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'TEACHER', 'ACTIVE')",
                teacherId, "teacher_cw_" + teacherId);
        jdbc.update("INSERT INTO public.orders (id, parent_id, teacher_id, total_amount, shipping_fee, status) VALUES (?, ?, ?, 200000, 0, 'PENDING_PAYMENT')",
                orderId, parentId, teacherId);
        jdbc.update("INSERT INTO public.payments (id, order_id, teacher_id, gateway, amount, commission_amount, net_amount, status) VALUES (?, ?, ?, 'MOCK', 200000, 40000, 160000, 'PENDING')",
                paymentId, orderId, teacherId);

        Map<String, String> params = Map.of(
                "paymentId", paymentId.toString(),
                "transactionId", sharedTxId,
                "amount", "200000",
                "status", "SUCCESS",
                "signature", "MOCK_VALID_SIGNATURE"
        );

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch endLatch = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.atomic.AtomicInteger successResponses = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Map<String, Object> res = paymentService.handleWebhook("MOCK", params);
                    if ("SUCCESS".equals(res.get("status"))) {
                        successResponses.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, java.util.concurrent.TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(2, successResponses.get(), "Cả 2 luồng đều nhận phản hồi SUCCESS (1 xử lý chính, 1 idempotent)");
        assertEquals(com.example.art_course.common.statemachine.OrderStatus.PAID, orderRepo.findById(orderId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.HELD, paymentRepo.findById(paymentId).orElseThrow().getStatus());

        Integer ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.money_ledger WHERE payment_id = ? AND entry_type = 'HOLD'",
                Integer.class, paymentId
        );
        assertEquals(1, ledgerCount, "Chỉ được phép có đúng 1 bút toán HOLD trong money_ledger");
    }

    // =========================================================================
    // TEST 8: ROLLBACK TOÀN BỘ KHI LỖI GIỮA TRANSACTION THANH TOÁN (POSTGRESQL THẬT)
    // =========================================================================
    @Test
    @DisplayName("8. Rollback toàn bộ khi lỗi giữa transaction thanh toán trên PostgreSQL thật: đơn, payment, kho và sổ cái giữ nguyên")
    void testWebhook_RollbackAllOnMidTransactionError_OnRealPostgres() {
        var ctx = com.example.art_course.common.PostgresTestcontainerEnv.getContext();
        var jdbc = ctx.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var txManager = ctx.getBean(org.springframework.transaction.PlatformTransactionManager.class);
        var orderRepo = ctx.getBean(OrderRepository.class);
        var detailRepo = ctx.getBean(OrderDetailRepository.class);
        var paymentRepo = ctx.getBean(PaymentRepository.class);
        var webhookLogRepo = ctx.getBean(WebhookLogRepository.class);
        var supplyRepo = ctx.getBean(SupplyRepository.class);
        var gatewayFactory = ctx.getBean(PaymentGatewayFactory.class);
        var grantService = ctx.getBean(EnrollmentGrantService.class);
        var notifService = ctx.getBean(NotificationService.class);
        var configService = ctx.getBean(ConfigService.class);

        UUID parentId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'PARENT', 'ACTIVE')",
                parentId, "parent_rb_" + parentId);
        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'TEACHER', 'ACTIVE')",
                teacherId, "teacher_rb_" + teacherId);
        jdbc.update("INSERT INTO public.supplies (id, teacher_id, name, price, stock_quantity, reserved_quantity, sold_quantity, status) VALUES (?, ?, 'Giay ve', 50000, 10, 2, 0, 'ACTIVE')",
                supplyId, teacherId);
        jdbc.update("INSERT INTO public.orders (id, parent_id, teacher_id, total_amount, shipping_fee, status) VALUES (?, ?, ?, 130000, 30000, 'PENDING_PAYMENT')",
                orderId, parentId, teacherId);
        jdbc.update("INSERT INTO public.order_details (order_id, supply_id, product_name, unit_price, quantity, sub_total, delivery_status) VALUES (?, ?, 'Giay ve', 50000, 2, 100000, 'PENDING')",
                orderId, supplyId);
        jdbc.update("INSERT INTO public.payments (id, order_id, teacher_id, gateway, amount, commission_amount, net_amount, status) VALUES (?, ?, ?, 'MOCK', 130000, 10000, 120000, 'PENDING')",
                paymentId, orderId, teacherId);

        // Giả lập lỗi giữa transaction khi ghi sổ cái money_ledger (sau khi order, payment, supply đã save trong cùng @Transactional)
        MoneyLedgerRepository failingLedgerRepo = mock(MoneyLedgerRepository.class);
        when(failingLedgerRepo.save(any())).thenThrow(new RuntimeException("Simulated mid-transaction DB failure on money_ledger"));

        PaymentService rawService = new PaymentService(
                orderRepo, detailRepo, paymentRepo, webhookLogRepo,
                failingLedgerRepo, supplyRepo, gatewayFactory, grantService, notifService, configService
        );

        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(txManager);

        Map<String, String> params = Map.of(
                "paymentId", paymentId.toString(),
                "transactionId", "TX_ROLLBACK_" + UUID.randomUUID(),
                "amount", "130000",
                "status", "SUCCESS",
                "signature", "MOCK_VALID_SIGNATURE"
        );

        assertThrows(RuntimeException.class, () ->
                txTemplate.executeWithoutResult(status -> rawService.handleWebhook("MOCK", params))
        );

        // Kiểm chứng toàn bộ thay đổi trên PostgreSQL thật đã bị ROLLBACK sạch sẽ
        assertEquals(com.example.art_course.common.statemachine.OrderStatus.PENDING_PAYMENT,
                orderRepo.findById(orderId).orElseThrow().getStatus(), "Order phải rollback về PENDING_PAYMENT");
        assertEquals(PaymentStatus.PENDING,
                paymentRepo.findById(paymentId).orElseThrow().getStatus(), "Payment phải rollback về PENDING");

        var supplyAfter = supplyRepo.findById(supplyId).orElseThrow();
        assertEquals(2, supplyAfter.getReservedQuantity(), "Tồn giữ chỗ phải rollback về 2");
        assertEquals(0, supplyAfter.getSoldQuantity(), "Tồn đã bán phải rollback về 0");

        Integer ledgerRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.money_ledger WHERE payment_id = ?", Integer.class, paymentId);
        assertEquals(0, ledgerRows, "Không có bản ghi nào trong money_ledger");
    }
}
