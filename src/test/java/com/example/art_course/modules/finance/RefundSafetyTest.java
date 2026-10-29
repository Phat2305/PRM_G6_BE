package com.example.art_course.modules.finance;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.*;
import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.fulfillment.service.FulfillmentService;
import com.example.art_course.modules.moderation.service.ModerationService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.*;
import com.example.art_course.modules.order.repository.*;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.*;
import com.example.art_course.modules.refund.dto.*;
import com.example.art_course.modules.refund.entity.*;
import com.example.art_course.modules.refund.repository.*;
import com.example.art_course.modules.refund.service.RefundService;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import com.example.art_course.supply.entity.Supply;
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

class RefundSafetyTest {
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final RefundItemRepository items = mock(RefundItemRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final SupplyRepository supplies = mock(SupplyRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final RefundService service = new RefundService(refunds, items, orders, details, payments, supplies,
            mock(IEnrollmentContract.class), ledger, notifications, mock(ModerationService.class),
            mock(CustomizeRequestRepository.class), mock(CourseRepository.class));
    private Order order;
    private OrderDetail line;
    private Payment payment;
    private Refund refund;
    private Supply supply;

    @BeforeEach
    void setUp() {
        order = Order.builder().id(UUID.randomUUID()).parentId(UUID.randomUUID()).teacherId(UUID.randomUUID()).status(OrderStatus.PAID).build();
        payment = Payment.builder().id(UUID.randomUUID()).orderId(order.getId()).teacherId(order.getTeacherId())
                .amount(new BigDecimal("100000")).netAmount(new BigDecimal("90000")).status(PaymentStatus.HELD).build();
        supply = Supply.builder().id(UUID.randomUUID()).stockQuantity(9).soldQuantity(1).reservedQuantity(0).status("ACTIVE").build();
        line = OrderDetail.builder().id(UUID.randomUUID()).orderId(order.getId()).supplyId(supply.getId())
                .subTotal(payment.getAmount()).commissionAmount(new BigDecimal("10000")).netAmount(payment.getNetAmount())
                .quantity(1).deliveryStatus(DeliveryStatus.PENDING).build();
        refund = Refund.builder().id(UUID.randomUUID()).orderId(order.getId()).paymentId(payment.getId())
                .parentId(order.getParentId()).status("PENDING").totalAmount(payment.getAmount()).build();
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(details.findOrderIdById(line.getId())).thenReturn(Optional.of(order.getId()));
        when(details.findById(line.getId())).thenReturn(Optional.of(line));
        when(refunds.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        when(refunds.save(any())).thenAnswer(i -> {
            Refund r = i.getArgument(0);
            if (r.getId() == null) r.setId(refund.getId());
            return r;
        });
        when(supplies.findByIdForUpdate(supply.getId())).thenReturn(Optional.of(supply));
        when(items.findByRefundId(refund.getId())).thenReturn(List.of(RefundItem.builder()
                .refundId(refund.getId()).orderDetailId(line.getId()).amount(line.getSubTotal()).build()));
        when(refunds.sumCompletedByPaymentId(payment.getId())).thenReturn(payment.getAmount());
        when(refunds.sumCompletedTeacherAmount(payment.getId())).thenReturn(BigDecimal.ZERO);
    }

    private CreateRefundRequest request() {
        return CreateRefundRequest.builder().orderId(order.getId()).orderDetailId(line.getId())
                .reason("Đổi nhu cầu").bankInfo("Ngân hàng - người nhận").build();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "FAILED", "REFUNDED"})
    void unpaidOrFullyRefundedPaymentCannotRequestAnotherRefund(PaymentStatus status) {
        payment.setStatus(status);
        assertEquals("PAYMENT_NOT_PAID", assertThrows(AppError.class,
                () -> service.requestRefund(order.getParentId(), request())).getErrorCode());
        verify(refunds, never()).save(any());
    }

    @Test
    void sameLineCannotConsumeTheBudgetOfOtherLines() {
        payment.setAmount(new BigDecimal("300000"));
        when(items.hasNonRejectedRefund(line.getId())).thenReturn(true);
        assertEquals("DUPLICATE_REFUND", assertThrows(AppError.class,
                () -> service.requestRefund(order.getParentId(), request())).getErrorCode());
        verify(refunds, never()).save(any());
    }

    @Test
    void parentCancellationThenRefundApprovalRestoresInventoryExactlyOnce() {
        FulfillmentService fulfillment = new FulfillmentService(details, orders, supplies, payments,
                refunds, items, notifications, mock(ConfigService.class));
        fulfillment.parentCancelItem(line.getId(), order.getParentId(), "Không cần nữa");
        assertEquals(10, supply.getStockQuantity());
        assertEquals(DeliveryStatus.CANCELLED, line.getDeliveryStatus());
        service.approveRefund(refund.getId(), UUID.randomUUID());
        assertEquals(10, supply.getStockQuantity());
        assertEquals(DeliveryStatus.REFUNDED, line.getDeliveryStatus());
        verify(supplies, times(1)).save(supply);
    }

    @Test
    void latePaymentRefundDoesNotReturnStockThatWasNeverSold() {
        order.setStatus(OrderStatus.EXPIRED);
        service.approveRefund(refund.getId(), UUID.randomUUID());
        assertEquals(9, supply.getStockQuantity());
        verifyNoInteractions(supplies);
    }

    private CompleteRefundRequest completion() {
        CompleteRefundRequest r = new CompleteRefundRequest();
        r.setTransactionCode("bank-tx");
        r.setProofImageUrl("https://example.com/proof.png");
        return r;
    }

    @Test
    void fullHeldRefundRemovesHeldBalanceWithoutDebitingOtherOrders() {
        refund.setStatus("APPROVED");
        service.completeRefund(refund.getId(), order.getTeacherId(), completion());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(ledger).recordEntry(eq(order.getTeacherId()), eq("REFUND"), eq(new BigDecimal("90000")),
                eq(payment.getId()), eq(refund.getId()), isNull(), anyString(), eq(order.getTeacherId()), eq(false));
        MoneyLedgerRepository entries = mock(MoneyLedgerRepository.class);
        when(payments.findByTeacherIdOrderByCreatedAtDesc(order.getTeacherId())).thenReturn(List.of(payment));
        LedgerService balances = new LedgerService(entries, payments, mock(WithdrawalRequestRepository.class), refunds);
        assertEquals(BigDecimal.ZERO, balances.getTeacherBalance(order.getTeacherId()).getHeldBalance());
    }

    @Test
    void partialHeldRefundOnlyRemovesItsTeacherShare() {
        payment.setAmount(new BigDecimal("200000"));
        payment.setNetAmount(new BigDecimal("180000"));
        refund.setStatus("APPROVED");
        when(refunds.sumCompletedByPaymentId(payment.getId())).thenReturn(new BigDecimal("100000"));
        when(refunds.sumCompletedTeacherAmount(payment.getId())).thenReturn(new BigDecimal("90000"));
        service.completeRefund(refund.getId(), order.getTeacherId(), completion());
        assertEquals(PaymentStatus.HELD, payment.getStatus());
        when(payments.findByTeacherIdOrderByCreatedAtDesc(order.getTeacherId())).thenReturn(List.of(payment));
        LedgerService balances = new LedgerService(mock(MoneyLedgerRepository.class), payments,
                mock(WithdrawalRequestRepository.class), refunds);
        assertEquals(new BigDecimal("90000"), balances.getTeacherBalance(order.getTeacherId()).getHeldBalance());
    }

    @Test
    void refundAfterReleaseDebitsOnlyTeacherNet() {
        payment.setStatus(PaymentStatus.RELEASED);
        refund.setStatus("APPROVED");
        service.completeRefund(refund.getId(), order.getTeacherId(), completion());
        verify(ledger).recordEntry(eq(order.getTeacherId()), eq("REFUND"), eq(new BigDecimal("90000")),
                eq(payment.getId()), eq(refund.getId()), isNull(), anyString(), eq(order.getTeacherId()), eq(true));
    }
}
