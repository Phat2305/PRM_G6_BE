package com.example.art_course.modules.finance;

import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LedgerHeldOrdersTest {
    @Test
    void showsOnlyUnreleasedNetRemainingAfterRefund() {
        UUID teacher = UUID.randomUUID();
        PaymentRepository payments = mock(PaymentRepository.class);
        RefundRepository refunds = mock(RefundRepository.class);
        MoneyLedgerRepository entries = mock(MoneyLedgerRepository.class);
        WithdrawalRequestRepository withdrawals = mock(WithdrawalRequestRepository.class);
        Payment held = Payment.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID())
                .status(PaymentStatus.HELD).netAmount(BigDecimal.valueOf(200000)).build();
        Payment released = Payment.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID())
                .status(PaymentStatus.RELEASED).netAmount(BigDecimal.valueOf(300000)).build();
        when(payments.findByTeacherIdOrderByCreatedAtDesc(teacher)).thenReturn(List.of(held, released));
        when(refunds.sumCompletedTeacherAmount(held.getId())).thenReturn(BigDecimal.valueOf(50000));
        var balance = new LedgerService(entries, payments, withdrawals, refunds).getTeacherBalance(teacher);
        assertEquals(BigDecimal.valueOf(150000), balance.getHeldBalance());
        assertEquals(List.of(new com.example.art_course.modules.finance.dto.TeacherBalanceDto.HeldOrder(
                held.getOrderId(), BigDecimal.valueOf(150000))), balance.getHeldOrders());
    }
}
