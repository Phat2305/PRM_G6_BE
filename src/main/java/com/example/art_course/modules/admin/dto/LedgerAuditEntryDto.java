package com.example.art_course.modules.admin.dto;

import com.example.art_course.modules.payment.entity.MoneyLedger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// ADM-04: một bút toán sổ cái kèm tên giáo viên để Admin đọc được.
public record LedgerAuditEntryDto(
        UUID id,
        UUID teacherId,
        String teacherName,
        String entryType,
        BigDecimal amount,
        UUID paymentId,
        UUID refundId,
        UUID withdrawalId,
        String note,
        UUID createdBy,
        Instant createdAt,
        AuditDetailsDto details
) {
    public static LedgerAuditEntryDto of(MoneyLedger m, String teacherName, AuditDetailsDto details) {
        return new LedgerAuditEntryDto(m.getId(), m.getTeacherId(), teacherName, m.getEntryType(), m.getAmount(),
                m.getPaymentId(), m.getRefundId(), m.getWithdrawalId(), m.getNote(), m.getCreatedBy(),
                m.getCreatedAt(), details);
    }
}
