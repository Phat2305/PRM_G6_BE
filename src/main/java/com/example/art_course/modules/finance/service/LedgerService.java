package com.example.art_course.modules.finance.service;

import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.modules.finance.dto.TeacherBalanceDto;
import com.example.art_course.modules.payment.entity.MoneyLedger;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.withdrawal.entity.WithdrawalRequest;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final MoneyLedgerRepository moneyLedgerRepository;
    private final PaymentRepository paymentRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final com.example.art_course.modules.refund.repository.RefundRepository refundRepository;

    /**
     * Tính toán số dư thời gian thực của Teacher trực tiếp từ sổ cái và các giao dịch (FIN-03).
     */
    @Transactional(readOnly = true)
    public TeacherBalanceDto getTeacherBalance(UUID teacherId) {
        // 1. Tiền đang giữ (Escrow / HELD)
        List<Payment> payments = paymentRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
        List<TeacherBalanceDto.HeldOrder> heldOrders = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.HELD)
                .map(p -> new TeacherBalanceDto.HeldOrder(p.getOrderId(),
                        p.getNetAmount().subtract(refundRepository.sumCompletedTeacherAmount(p.getId())).max(BigDecimal.ZERO)))
                .filter(p -> p.amount().signum() > 0).toList();
        BigDecimal heldBalance = heldOrders.stream()
                .map(TeacherBalanceDto.HeldOrder::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Tiền có thể rút (Available Balance từ sổ cái money_ledger)
        BigDecimal availableBalance = moneyLedgerRepository.calculateAvailableBalance(teacherId);
        if (availableBalance == null || availableBalance.compareTo(BigDecimal.ZERO) < 0) {
            availableBalance = BigDecimal.ZERO;
        }

        // 3. Tiền đang chờ rút (PENDING hoặc APPROVED)
        List<WithdrawalRequest> withdrawals = withdrawalRequestRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
        BigDecimal pendingBalance = withdrawals.stream()
                .filter(w -> w.getStatus() == WithdrawalStatus.PENDING || w.getStatus() == WithdrawalStatus.APPROVED)
                .map(WithdrawalRequest::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Tiền đã rút (PAID)
        BigDecimal withdrawnBalance = withdrawals.stream()
                .filter(w -> w.getStatus() == WithdrawalStatus.PAID)
                .map(WithdrawalRequest::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Lịch sử sổ cái
        List<MoneyLedger> history = moneyLedgerRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);

        return TeacherBalanceDto.builder()
                .teacherId(teacherId)
                .heldBalance(heldBalance)
                .availableBalance(availableBalance)
                .pendingBalance(pendingBalance)
                .withdrawnBalance(withdrawnBalance)
                .history(history)
                .heldOrders(heldOrders)
                .build();
    }

    /**
     * Ghi nhận một bút toán vào sổ cái money_ledger.
     */
    @Transactional
    public MoneyLedger recordEntry(UUID teacherId, String entryType, BigDecimal amount,
                                  UUID paymentId, UUID refundId, UUID withdrawalId,
                                  String note, UUID createdBy) {
        return recordEntry(teacherId, entryType, amount, paymentId, refundId, withdrawalId, note, createdBy, true);
    }

    @Transactional
    public MoneyLedger recordEntry(UUID teacherId, String entryType, BigDecimal amount,
                                  UUID paymentId, UUID refundId, UUID withdrawalId,
                                  String note, UUID createdBy, boolean affectsAvailable) {
        MoneyLedger entry = MoneyLedger.builder()
                .teacherId(teacherId)
                .entryType(entryType)
                .amount(amount)
                .affectsAvailable(affectsAvailable)
                .paymentId(paymentId)
                .refundId(refundId)
                .withdrawalId(withdrawalId)
                .note(note)
                .createdBy(createdBy)
                .build();
        return moneyLedgerRepository.save(entry);
    }
}
