package com.example.art_course.modules.admin.service;

import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.modules.admin.dto.AdminAnalyticsDto;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.MoneyLedger;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.repository.RefundRepository;
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
public class AdminService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final RefundRepository refundRepository;
    private final MoneyLedgerRepository moneyLedgerRepository;

    /**
     * Thống kê tài chính toàn diện sàn (ADM-04, ADM-05, ADM-06).
     */
    @Transactional(readOnly = true)
    public AdminAnalyticsDto getFinanceAnalytics() {
        List<Payment> allPayments = paymentRepository.findAll();

        // 1. Doanh thu: tổng tiền các đơn đã thanh toán thành công (HELD + RELEASED)
        BigDecimal totalRevenue = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.HELD || p.getStatus() == PaymentStatus.RELEASED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Tiền đang giữ Escrow (HELD)
        BigDecimal totalHeld = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.HELD)
                .map(Payment::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Hoa hồng sàn đã thu
        BigDecimal totalCommission = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.HELD || p.getStatus() == PaymentStatus.RELEASED)
                .map(Payment::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Rút tiền
        List<WithdrawalRequest> allWithdrawals = withdrawalRequestRepository.findAll();
        BigDecimal totalPendingWithdrawals = allWithdrawals.stream()
                .filter(w -> w.getStatus() == WithdrawalStatus.PENDING || w.getStatus() == WithdrawalStatus.APPROVED)
                .map(WithdrawalRequest::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaidOut = allWithdrawals.stream()
                .filter(w -> w.getStatus() == WithdrawalStatus.PAID)
                .map(WithdrawalRequest::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Hoàn tiền
        List<Refund> allRefunds = refundRepository.findAll();
        BigDecimal totalRefunded = allRefunds.stream()
                .filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus()))
                .map(Refund::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 6. Số dư khả dụng toàn sàn (tính từ ledger)
        List<MoneyLedger> allLedgers = moneyLedgerRepository.findAll();
        BigDecimal totalAvailable = BigDecimal.ZERO;
        for (MoneyLedger l : allLedgers) {
            if ("RELEASE".equals(l.getEntryType()) || "WITHDRAW_UNLOCK".equals(l.getEntryType())) {
                totalAvailable = totalAvailable.add(l.getAmount());
            } else if ("WITHDRAW_LOCK".equals(l.getEntryType()) || "REFUND".equals(l.getEntryType())) {
                totalAvailable = totalAvailable.subtract(l.getAmount());
            }
        }
        if (totalAvailable.compareTo(BigDecimal.ZERO) < 0) totalAvailable = BigDecimal.ZERO;

        long totalOrders = orderRepository.count();
        long paidOrders = orderRepository.findByStatus(OrderStatus.PAID).size();

        return AdminAnalyticsDto.builder()
                .totalRevenue(totalRevenue)
                .totalHeld(totalHeld)
                .totalAvailable(totalAvailable)
                .totalPendingWithdrawals(totalPendingWithdrawals)
                .totalPaidOut(totalPaidOut)
                .totalRefunded(totalRefunded)
                .totalCommission(totalCommission)
                .totalOrders(totalOrders)
                .paidOrders(paidOrders)
                .build();
    }

    /**
     * Tra cứu sổ cái tài chính toàn sàn (Audit log tiền tệ - ADM-04).
     */
    @Transactional(readOnly = true)
    public List<MoneyLedger> getLedgerAuditLogs(UUID teacherId, String entryType) {
        if (teacherId != null) {
            return moneyLedgerRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
        }
        return moneyLedgerRepository.findAll();
    }
}
