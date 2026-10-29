package com.example.art_course.modules.payment;

import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.dto.PaymentStatusResponseDto;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Hủy thanh toán ở cổng: báo cho app nhưng không đổi trạng thái thanh toán")
class PaymentCancelStatusTests {

    private final PaymentRepository paymentRepo = mock(PaymentRepository.class);
    private final OrderRepository orderRepo = mock(OrderRepository.class);
    private final PaymentGatewayFactory gatewayFactory = mock(PaymentGatewayFactory.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentService service = new PaymentService(
            orderRepo, mock(OrderDetailRepository.class), paymentRepo, mock(WebhookLogRepository.class),
            mock(MoneyLedgerRepository.class), mock(SupplyRepository.class), gatewayFactory, null,
            mock(NotificationService.class), null, org.mockito.Mockito.mock(com.example.art_course.modules.refund.repository.RefundRepository.class), org.mockito.Mockito.mock(com.example.art_course.modules.refund.repository.RefundItemRepository.class), org.mockito.Mockito.mock(com.example.art_course.enrollment.repository.EnrollmentRepository.class), org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));

    private final UUID parent = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final Payment payment = Payment.builder().id(paymentId).orderId(UUID.randomUUID()).gateway("PAYOS")
            .status(PaymentStatus.PENDING).amount(new BigDecimal("1000")).netAmount(new BigDecimal("900"))
            .teacherId(UUID.randomUUID()).createdAt(Instant.now()).build();

    private PaymentStatusResponseDto query(PaymentGateway.GatewayQueryResult result) {
        when(paymentRepo.findOrderIdById(paymentId)).thenReturn(Optional.of(payment.getOrderId()));
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(orderRepo.findByIdForUpdate(payment.getOrderId())).thenReturn(Optional.of(Order.builder().id(payment.getOrderId())
                .parentId(parent).teacherId(payment.getTeacherId()).build()));
        when(gatewayFactory.getGateway("PAYOS")).thenReturn(gateway);
        when(gateway.queryTransaction(payment)).thenReturn(result);
        return service.queryPaymentStatus(paymentId, parent, "PARENT");
    }

    @Test
    @DisplayName("cổng báo đã hủy: cancelled=true, payment vẫn PENDING để thanh toán lại")
    void cancelledAtGateway() {
        PaymentStatusResponseDto dto = query(new PaymentGateway.GatewayQueryResult(true, false, "id", payment.getAmount(), true));

        assertTrue(dto.isCancelled());
        assertEquals(PaymentStatus.PENDING, dto.getStatus());
        verify(paymentRepo, never()).transition(eq(paymentId), any(), any());
    }

    @Test
    @DisplayName("link còn hiệu lực, chưa trả: cancelled=false")
    void stillWaiting() {
        PaymentStatusResponseDto dto = query(new PaymentGateway.GatewayQueryResult(true, false, "id", payment.getAmount()));

        assertFalse(dto.isCancelled());
        assertEquals(PaymentStatus.PENDING, dto.getStatus());
    }
}
