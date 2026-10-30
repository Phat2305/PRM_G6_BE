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
    private final ChildrenCourseRepository childrenCourses = mock(ChildrenCourseRepository.class);
    private final com.example.art_course.child_progress.repository.ChildProgressRepository progress =
            mock(com.example.art_course.child_progress.repository.ChildProgressRepository.class);
    private final com.example.art_course.lesson.repository.LessonRepository lessons =
            mock(com.example.art_course.lesson.repository.LessonRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ConfigService config = mock(ConfigService.class);
    private final com.example.art_course.customize_request.repository.CustomizeRequestRepository customizeRequests =
            mock(com.example.art_course.customize_request.repository.CustomizeRequestRepository.class);
    private final EscrowReleaseService service = new EscrowReleaseService(payments, orders, details,
            childrenCourses,
            mock(CourseReviewRepository.class), refunds, ledger, notifications, config,
                    progress, lessons,
                    customizeRequests);
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
    void customizeAutoReleaseStartsAfterApprovedDelivery() {
        UUID requestId = UUID.randomUUID();
        OrderDetail line = OrderDetail.builder().customizeRequestId(requestId).build();
        when(details.findByOrderId(order.getId())).thenReturn(List.of(line));
        when(config.getCustomizeAutoConfirmDays()).thenReturn(Optional.of(7));
        assertFalse(service.tryReleaseEscrow(order.getId()));

        line.setDeliveredAt(java.time.Instant.now().minus(8, java.time.temporal.ChronoUnit.DAYS));
        var request = com.example.art_course.customize_request.entity.CustomizeRequest.builder()
                .id(requestId).status("DELIVERED").build();
        when(customizeRequests.findById(requestId)).thenReturn(Optional.of(request));
        when(payments.transition(payment.getId(), PaymentStatus.HELD, PaymentStatus.RELEASED)).thenReturn(1);
        assertTrue(service.tryReleaseEscrow(order.getId()));
    }

    @Test
    void bothAssignedChildrenMustFinishBeforeCourseEscrowReleases() {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(details.findByOrderId(order.getId())).thenReturn(List.of(
                OrderDetail.builder().courseId(courseId).childId(first).build()));
        when(childrenCourses.findByOrderIdAndCourseId(order.getId(), courseId)).thenReturn(List.of(
                ChildrenCourse.builder().childId(first).build(), ChildrenCourse.builder().childId(second).build()));
        when(lessons.findAllByCourseId(courseId)).thenReturn(List.of(
                com.example.art_course.lesson.entity.Lesson.builder().id(lessonId).build()));
        var completed = com.example.art_course.child_progress.entity.ChildProgress.builder()
                .id(new com.example.art_course.child_progress.entity.ChildProgressId(first, lessonId))
                .isCompleted(true).build();
        when(progress.findAllByIdChildId(first)).thenReturn(List.of(completed));
        assertFalse(service.tryReleaseEscrow(order.getId()));

        when(progress.findAllByIdChildId(second)).thenReturn(List.of(
                com.example.art_course.child_progress.entity.ChildProgress.builder()
                        .id(new com.example.art_course.child_progress.entity.ChildProgressId(second, lessonId))
                        .isCompleted(true).build()));
        when(payments.transition(payment.getId(), PaymentStatus.HELD, PaymentStatus.RELEASED)).thenReturn(1);
        assertTrue(service.tryReleaseEscrow(order.getId()));
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
