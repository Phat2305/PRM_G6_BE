package com.example.art_course.jobs;

import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.gateway.PaymentGateway;
import com.example.art_course.modules.payment.gateway.PaymentGatewayFactory;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.payment.repository.WebhookLogRepository;
import com.example.art_course.modules.payment.service.PaymentService;
import com.example.art_course.supply.repository.SupplyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Đối soát thanh toán: mỗi giao dịch một transaction, lỗi một cái không chặn các cái khác")
class PaymentReconcileTests {

    private final PaymentRepository paymentRepo = mock(PaymentRepository.class);
    private final OrderRepository orderRepo = mock(OrderRepository.class);
    private final PaymentGatewayFactory gatewayFactory = mock(PaymentGatewayFactory.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentService service = new PaymentService(
            orderRepo, mock(OrderDetailRepository.class), paymentRepo, mock(WebhookLogRepository.class),
            mock(MoneyLedgerRepository.class), mock(SupplyRepository.class), gatewayFactory, null,
            mock(NotificationService.class), null);

    private Payment pending(UUID id) {
        return Payment.builder().id(id).orderId(UUID.randomUUID()).gateway("PAYOS").status(PaymentStatus.PENDING)
                .amount(new BigDecimal("1000")).netAmount(new BigDecimal("900")).teacherId(UUID.randomUUID())
                .createdAt(Instant.now()).build();
    }

    @Test
    @DisplayName("reconcilePayment chạy trong @Transactional ghi được, để lỗi giữa chừng rollback toàn bộ")
    void runsInsideWritableTransaction() throws Exception {
        Transactional tx = PaymentService.class.getMethod("reconcilePayment", UUID.class).getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertFalse(tx.readOnly());
    }

    @Test
    @DisplayName("đã trả ở cổng thì xử lý và trả true; chưa trả hoặc đã xử lý rồi thì không làm gì")
    void reconcileOutcomes() {
        UUID paidId = UUID.randomUUID();
        Payment paid = pending(paidId);
        when(paymentRepo.findById(paidId)).thenReturn(Optional.of(paid));
        when(paymentRepo.transition(paidId, PaymentStatus.PENDING, PaymentStatus.HELD)).thenReturn(1);
        when(orderRepo.findById(paid.getOrderId())).thenReturn(Optional.of(Order.builder().id(paid.getOrderId())
                .parentId(UUID.randomUUID()).teacherId(UUID.randomUUID()).build()));
        when(gatewayFactory.getGateway("PAYOS")).thenReturn(gateway);
        when(gateway.queryTransaction(paid)).thenReturn(new PaymentGateway.GatewayQueryResult(true, true, "tx", paid.getAmount()));
        assertTrue(service.reconcilePayment(paidId));
        verify(paymentRepo).transition(paidId, PaymentStatus.PENDING, PaymentStatus.HELD);

        UUID unpaidId = UUID.randomUUID();
        Payment unpaid = pending(unpaidId);
        when(paymentRepo.findById(unpaidId)).thenReturn(Optional.of(unpaid));
        when(gateway.queryTransaction(unpaid)).thenReturn(new PaymentGateway.GatewayQueryResult(true, false, null, null));
        assertFalse(service.reconcilePayment(unpaidId));
        verify(paymentRepo, never()).transition(eq(unpaidId), any(), any());

        UUID doneId = UUID.randomUUID();
        Payment done = pending(doneId);
        done.setStatus(PaymentStatus.HELD);
        when(paymentRepo.findById(doneId)).thenReturn(Optional.of(done));
        assertFalse(service.reconcilePayment(doneId));
        verify(gateway, never()).queryTransaction(done);
    }

    @Test
    @DisplayName("job: một giao dịch ném lỗi thì ghi log và đi tiếp, đếm đúng số giao dịch đã đồng bộ")
    void failingPaymentDoesNotStopTheJob() {
        PaymentService mockService = mock(PaymentService.class);
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        when(mockService.findReconcilablePaymentIds()).thenReturn(List.of(bad, good));
        when(mockService.reconcilePayment(bad)).thenThrow(new IllegalStateException("gateway down"));
        when(mockService.reconcilePayment(good)).thenReturn(true);

        int reconciled = new PaymentReconcileJob(mockService, null).reconcileAll();

        assertEquals(1, reconciled);
        verify(mockService).reconcilePayment(good);
    }
}
