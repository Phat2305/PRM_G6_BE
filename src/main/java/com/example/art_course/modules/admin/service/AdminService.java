package com.example.art_course.modules.admin.service;

import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.modules.admin.dto.AdminAnalyticsDto;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.MoneyLedger;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
     * Cộng dồn ngay trong DB (GROUP BY/SUM) thay vì nạp mọi dòng vào bộ nhớ.
     */
    @Transactional(readOnly = true)
    public AdminAnalyticsDto getFinanceAnalytics() {
        // 1-3. Doanh thu = HELD + RELEASED, tiền đang giữ = net của HELD, hoa hồng = HELD + RELEASED
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalHeld = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        for (Object[] row : paymentRepository.sumsByStatus(List.of(PaymentStatus.HELD, PaymentStatus.RELEASED))) {
            totalRevenue = totalRevenue.add((BigDecimal) row[1]);
            totalCommission = totalCommission.add((BigDecimal) row[3]);
            if (row[0] == PaymentStatus.HELD) {
                totalHeld = (BigDecimal) row[2];
            }
        }

        // 4. Rút tiền: đang chờ = PENDING + APPROVED, đã chi = PAID
        BigDecimal totalPendingWithdrawals = BigDecimal.ZERO;
        BigDecimal totalPaidOut = BigDecimal.ZERO;
        for (Object[] row : withdrawalRequestRepository.sumByStatus(
                List.of(WithdrawalStatus.PENDING, WithdrawalStatus.APPROVED, WithdrawalStatus.PAID))) {
            if (row[0] == WithdrawalStatus.PAID) {
                totalPaidOut = (BigDecimal) row[1];
            } else {
                totalPendingWithdrawals = totalPendingWithdrawals.add((BigDecimal) row[1]);
            }
        }

        // 5-6. Hoàn tiền đã xong; số dư khả dụng toàn sàn tính từ ledger, không âm
        BigDecimal totalRefunded = refundRepository.sumCompleted();
        BigDecimal totalAvailable = moneyLedgerRepository.calculateTotalAvailable().max(BigDecimal.ZERO);

        return AdminAnalyticsDto.builder()
                .totalRevenue(totalRevenue)
                .totalHeld(totalHeld)
                .totalAvailable(totalAvailable)
                .totalPendingWithdrawals(totalPendingWithdrawals)
                .totalPaidOut(totalPaidOut)
                .totalRefunded(totalRefunded)
                .totalCommission(totalCommission)
                .totalOrders(orderRepository.count())
                .paidOrders(orderRepository.countByStatus(OrderStatus.PAID))
                .build();
    }

    /**
     * Tra cứu sổ cái tài chính toàn sàn (Audit log tiền tệ - ADM-04).
     */
    @Transactional(readOnly = true)
    public List<MoneyLedger> getLedgerAuditLogs(UUID teacherId, String entryType, int page, int size) {
        // id làm khóa phụ để các dòng cùng createdAt không bị lặp hoặc sót giữa các trang.
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        boolean byType = entryType != null && !entryType.isBlank();
        Page<MoneyLedger> result;
        if (teacherId != null && byType) {
            result = moneyLedgerRepository.findByTeacherIdAndEntryType(teacherId, entryType, pageable);
        } else if (teacherId != null) {
            result = moneyLedgerRepository.findByTeacherId(teacherId, pageable);
        } else if (byType) {
            result = moneyLedgerRepository.findByEntryType(entryType, pageable);
        } else {
            result = moneyLedgerRepository.findAll(pageable);
        }
        return result.getContent();
    }
}
