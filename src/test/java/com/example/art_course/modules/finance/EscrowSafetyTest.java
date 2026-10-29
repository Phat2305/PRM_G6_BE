package com.example.art_course.modules.finance;

import com.example.art_course.common.statemachine.*;
import com.example.art_course.modules.course.repository.CourseReviewRepository;
import com.example.art_course.modules.finance.service.*;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.*;
import com.example.art_course.modules.order.repository.*;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EscrowSafetyTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final EscrowReleaseService service = new EscrowReleaseService(payments, orders, details,
            mock(CourseReviewRepository.class), refunds, ledger, notifications, mock(ConfigService.class));
    private final Order order = Order.builder().id(UUID.randomUUID()).parentId(UUID.randomUUID())
            .teacherId(UUID.randomUUID()).status(OrderStatus.PAID).build();
    private final Payment payment = Payment.builder().id(UUID.randomUUID()).orderId(order.getId())
            .teacherId(order.getTeacherId()).status(PaymentStatus.HELD).netAmount(new BigDecimal("180000")).build();

    @BeforeEach
    void setUp() {
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
        when(details.findByOrderId(order.getId())).thenReturn(List.of(
                OrderDetail.builder().supplyId(UUID.randomUUID()).deliveryStatus(DeliveryStatus.COMPLETED).build(),
                OrderDetail.builder().supplyId(UUID.randomUUID()).deliveryStatus(DeliveryStatus.REFUNDED).build()));
        when(refunds.sumCompletedTeacherAmount(payment.getId())).thenReturn(new BigDecimal("90000"));
    }

    @Test
    void releaseCreditsOnlyUnrefundedNetAndCannotRepeat() {
        when(payments.transition(payment.getId(), PaymentStatus.HELD, PaymentStatus.RELEASED)).thenReturn(1);
        assertTrue(service.tryReleaseEscrow(order.getId()));
        assertEquals(PaymentStatus.RELEASED, payment.getStatus());
        assertFalse(service.tryReleaseEscrow(order.getId()));
        verify(ledger).recordEntry(eq(order.getTeacherId()), eq("RELEASE"), eq(new BigDecimal("90000")),
                eq(payment.getId()), isNull(), isNull(), anyString(), isNull());
    }

    @Test
    void lostAtomicTransitionNeverCreditsTheWallet() {
        assertFalse(service.tryReleaseEscrow(order.getId()));
        assertEquals(PaymentStatus.HELD, payment.getStatus());
        verifyNoInteractions(ledger, notifications);
    }

    @Test
    void pendingRefundOrCancelledOrderCannotBeReleased() {
        when(refunds.findByOrderId(order.getId())).thenReturn(List.of(Refund.builder().status("APPROVED").build()));
        assertFalse(service.tryReleaseEscrow(order.getId()));
        order.setStatus(OrderStatus.CANCELLED);
        assertFalse(service.tryReleaseEscrow(order.getId()));
        verify(payments, never()).transition(any(), any(), any());
        verifyNoInteractions(ledger);
    }
}
