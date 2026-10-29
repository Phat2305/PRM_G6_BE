package com.example.art_course.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "money_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoneyLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "entry_type", nullable = false, length = 30)
    private String entryType;

    @Column(nullable = false)
    private BigDecimal amount;

    // FIN-09: hoàn từ tiền đang giữ không trừ số dư đã giải ngân của đơn khác.
    @Column(name = "affects_available", nullable = false)
    @Builder.Default
    private boolean affectsAvailable = true;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "refund_id")
    private UUID refundId;

    @Column(name = "withdrawal_id")
    private UUID withdrawalId;

    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
