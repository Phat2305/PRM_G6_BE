package com.example.art_course.modules.payment;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.*;
import com.example.art_course.enrollment.entity.EnrollmentId;
import com.example.art_course.enrollment.repository.EnrollmentRepository;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.*;
import com.example.art_course.modules.order.repository.*;
import com.example.art_course.modules.payment.dto.CreatePaymentRequest;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.gateway.*;
import com.example.art_course.modules.payment.repository.*;
import com.example.art_course.modules.payment.service.*;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.repository.*;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.supply.repository.SupplyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentSafetyTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final MoneyLedgerRepository ledger = mock(MoneyLedgerRepository.class);
    private final SupplyRepository supplies = mock(SupplyRepository.class);
    private final PaymentGatewayFactory gateways = mock(PaymentGatewayFactory.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final EnrollmentGrantService grants = mock(EnrollmentGrantService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ConfigService config = mock(ConfigService.class);
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final RefundItemRepository refundItems = mock(RefundItemRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final org.springframework.transaction.PlatformTransactionManager transactions =
            mock(org.springframework.transaction.PlatformTransactionManager.class);
    private final PaymentService service = new PaymentService(orders, details, payments,
            mock(WebhookLogRepository.class), ledger, supplies, gateways, grants, notifications,
            config, refunds, refundItems, enrollments, transactions);
    private Order order;
    private Payment payment;
    private OrderDetail line;

    @BeforeEach
    void setUp() {
        order = Order.builder().id(UUID.randomUUID()).parentId(UUID.randomUUID()).teacherId(UUID.randomUUID())
                .status(OrderStatus.PENDING_PAYMENT).totalAmount(new BigDecimal("130000"))
                .shippingFee(new BigDecimal("30000")).build();
        payment = Payment.builder().id(UUID.randomUUID()).orderId(order.getId()).teacherId(order.getTeacherId())
                .gateway("PAYOS").gatewayOrderCode(1001L).paymentUrl("https://pay.payos.vn/old")
                .amount(order.getTotalAmount()).netAmount(new BigDecimal("120000"))
                .commissionAmount(new BigDecimal("10000")).status(PaymentStatus.PENDING).build();
        line = OrderDetail.builder().id(UUID.randomUUID()).orderId(order.getId()).supplyId(UUID.randomUUID())
                .quantity(1).subTotal(new BigDecimal("100000")).netAmount(new BigDecimal("90000"))
                .commissionAmount(new BigDecimal("10000")).deliveryStatus(DeliveryStatus.PENDING).build();
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findOrderIdById(payment.getId())).thenReturn(Optional.of(order.getId()));
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
        when(payments.findIdByGatewayOrderCode(1001L)).thenReturn(Optional.of(payment.getId()));
        when(details.findByOrderId(order.getId())).thenReturn(List.of(line));
        when(details.findOrderIdById(line.getId())).thenReturn(Optional.of(order.getId()));
        when(details.findById(line.getId())).thenReturn(Optional.of(line));
        when(payments.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(refunds.save(any())).thenAnswer(i -> {
            Refund r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(gateways.getGateway("PAYOS")).thenReturn(gateway);
        when(payments.transition(eq(payment.getId()), any(), any())).thenAnswer(i -> {
            if (payment.getStatus() != i.getArgument(1)) return 0;
            payment.setStatus(i.getArgument(2));
            return 1;
        });
        when(gateway.verifyCallback(any())).thenReturn(new PaymentGateway.GatewayCallbackResult(
                true, true, "tx", payment.getAmount(), "ok"));
    }

    private CreatePaymentRequest createRequest() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(order.getId());
        request.setGateway("PAYOS");
        request.setReturnUrl("https://example.com/return");
        return request;
    }

    // Số tiền ở link/QR PayOS phải đúng tổng đơn gồm cả phí ship (130.000 = 100.000 dụng cụ + 30.000 ship).
    @Test
    void newPaymentChargesTheOrderTotalIncludingShipping() {
        when(payments.findByOrderId(order.getId())).thenReturn(Optional.empty());
        when(config.getCommissionRateCourse()).thenReturn(new BigDecimal("0.20"));
        when(config.getCommissionRateSupply()).thenReturn(new BigDecimal("0.10"));
        when(gateway.createPaymentUrl(any(), any(), any())).thenReturn("https://pay.payos.vn/new");

        var response = service.createPaymentUrl(order.getParentId(), createRequest());

        assertEquals(0, new BigDecimal("130000").compareTo(response.getAmount()));
        verify(gateway).createPaymentUrl(eq(order), argThat(p -> p.getAmount().compareTo(new BigDecimal("130000")) == 0),
                eq("https://example.com/return"));
        // Giáo viên nhận tiền dụng cụ sau hoa hồng cộng nguyên phí ship.
        verify(payments, atLeastOnce()).save(argThat(p -> p.getNetAmount().compareTo(new BigDecimal("120000")) == 0));
    }

    // Giao dịch chờ thanh toán còn số tiền cũ (ví dụ tạo lúc tổng đơn chưa có phí ship) không được dùng lại link cũ.
    @Test
    void stalePendingPaymentIsCorrectedToTheOrderTotalWithANewLink() {
        payment.setAmount(new BigDecimal("100000"));
        payment.setNetAmount(new BigDecimal("90000"));
        when(config.getCommissionRateCourse()).thenReturn(new BigDecimal("0.20"));
        when(config.getCommissionRateSupply()).thenReturn(new BigDecimal("0.10"));
        when(gateway.createPaymentUrl(any(), any(), any())).thenReturn("https://pay.payos.vn/fixed");

        var response = service.createPaymentUrl(order.getParentId(), createRequest());

        assertEquals(0, new BigDecimal("130000").compareTo(payment.getAmount()));
        assertEquals(0, new BigDecimal("120000").compareTo(payment.getNetAmount()));
        assertEquals("https://pay.payos.vn/fixed", response.getPaymentUrl());
        verify(gateway, never()).queryTransaction(any());
    }

    @Test
    void matchingPendingPaymentKeepsItsExistingLink() {
        when(gateway.queryTransaction(payment)).thenReturn(
                new PaymentGateway.GatewayQueryResult(true, false, null, payment.getAmount(), false));

        var response = service.createPaymentUrl(order.getParentId(), createRequest());

        assertEquals("https://pay.payos.vn/old", response.getPaymentUrl());
        verify(gateway, never()).createPaymentUrl(any(), any(), any());
    }

    @Test
    void rejectsMockBeforeCallingGatewayOrRepositories() {
        assertEquals("UNSUPPORTED_GATEWAY", assertThrows(AppError.class,
                () -> service.handleWebhook("MOCK", Map.of("signature", "anything"))).getErrorCode());
        verifyNoInteractions(gateway, ledger, refunds, supplies);
    }

    @Test
    void callbackMustMatchStoredGateway() {
        payment.setGateway("MOCK");
        assertEquals("GATEWAY_MISMATCH", assertThrows(AppError.class,
                () -> service.handleWebhook("PAYOS", Map.of("data.orderCode", "1001"))).getErrorCode());
        verify(payments, never()).transition(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "EXPIRED"})
    void lateMoneyCreatesOneRefundWithoutRestoringOrderOrSellingStock(OrderStatus status) {
        order.setStatus(status);
        service.handleWebhook("PAYOS", Map.of("data.orderCode", "1001"));
        service.handleWebhook("PAYOS", Map.of("data.orderCode", "1001"));
        assertEquals(status, order.getStatus());
        assertEquals(PaymentStatus.HELD, payment.getStatus());
        verify(refunds).save(argThat(r -> r.getTotalAmount().compareTo(payment.getAmount()) == 0));
        verify(refundItems).save(argThat(r -> r.getOrderDetailId().equals(line.getId())));
        verify(ledger).save(any());
        verifyNoInteractions(supplies, grants);
        assertTrue(service.queryPaymentStatus(payment.getId(), order.getParentId(), "PARENT").isRefundPending());
    }

    private CreatePaymentRequest request() {
        CreatePaymentRequest r = new CreatePaymentRequest();
        r.setOrderId(order.getId());
        r.setGateway("PAYOS");
        r.setReturnUrl("https://example.com/result");
        return r;
    }

    @Test
    void retryReusesPendingLinkAndDoesNotRecalculateCommission() {
        when(gateway.queryTransaction(payment)).thenReturn(new PaymentGateway.GatewayQueryResult(true, false, null, BigDecimal.ZERO));
        assertEquals(payment.getPaymentUrl(), service.createPaymentUrl(order.getParentId(), request()).getPaymentUrl());
        verify(gateway, never()).createPaymentUrl(any(), any(), any());
        verifyNoInteractions(config);
    }

    @Test
    void unknownGatewayStateKeepsOldLink() {
        when(gateway.queryTransaction(payment)).thenReturn(new PaymentGateway.GatewayQueryResult(false, false, null, BigDecimal.ZERO));
        assertEquals(payment.getId(), service.createPaymentUrl(order.getParentId(), request()).getPaymentId());
        assertEquals(1001L, payment.getGatewayOrderCode());
        verify(gateway, never()).createPaymentUrl(any(), any(), any());
    }

    @Test
    void confirmedCancelledLinkCanBeReplaced() {
        when(gateway.queryTransaction(payment)).thenReturn(new PaymentGateway.GatewayQueryResult(true, false, null, BigDecimal.ZERO, true));
        when(gateway.createPaymentUrl(any(), any(), any())).thenAnswer(i -> {
            payment.setGatewayOrderCode(1002L);
            return "https://pay.payos.vn/new";
        });
        assertEquals("https://pay.payos.vn/new", service.createPaymentUrl(order.getParentId(), request()).getPaymentUrl());
        assertEquals(1002L, payment.getGatewayOrderCode());
        assertEquals(new BigDecimal("10000"), payment.getCommissionAmount());
    }

    @Test
    void queryRejectsPaidAmountMismatch() {
        when(gateway.queryTransaction(payment)).thenReturn(new PaymentGateway.GatewayQueryResult(true, true, "tx", BigDecimal.ONE));
        assertEquals("AMOUNT_MISMATCH", assertThrows(AppError.class,
                () -> service.queryPaymentStatus(payment.getId(), order.getParentId(), "PARENT")).getErrorCode());
        verifyNoInteractions(ledger, supplies, grants);
    }

    @Test
    void retryMissingGrantButNeverReactivateInactiveOrRefundedEnrollment() {
        order.setStatus(OrderStatus.PAID);
        payment.setStatus(PaymentStatus.HELD);
        line.setSupplyId(null);
        line.setCourseId(UUID.randomUUID());
        line.setChildId(UUID.randomUUID());
        when(enrollments.existsByIdChildIdAndIdCourseIdAndStatusIgnoreCase(line.getChildId(), line.getCourseId(), "active")).thenReturn(true);
        assertTrue(service.repairEnrollment(line.getId()));
        verify(grants).grantAccessWithRetry(line.getChildId(), line.getCourseId(), order.getId());
        clearInvocations(grants);
        when(enrollments.existsById(new EnrollmentId(line.getChildId(), line.getCourseId()))).thenReturn(true);
        assertFalse(service.repairEnrollment(line.getId()));
        verifyNoInteractions(grants);
        line.setDeliveryStatus(DeliveryStatus.REFUNDED);
        assertFalse(service.repairEnrollment(line.getId()));
        verifyNoInteractions(grants);
    }

    @Test
    void postCommitGrantRechecksIfARefundWonTheOrderLock() {
        line.setSupplyId(null);
        line.setCourseId(UUID.randomUUID());
        line.setChildId(UUID.randomUUID());
        when(transactions.getTransaction(any())).thenAnswer(i -> {
            line.setDeliveryStatus(DeliveryStatus.REFUNDED);
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        });
        service.handleWebhook("PAYOS", Map.of("data.orderCode", "1001"));
        assertEquals(PaymentStatus.HELD, payment.getStatus());
        verifyNoInteractions(grants);
        verify(transactions).commit(any());
    }
}
