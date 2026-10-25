package com.example.art_course.modules.withdrawal.dto;

import com.example.art_course.common.statemachine.WithdrawalStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalResponseDto {
    private UUID id;
    private UUID teacherId;
    // Họ tên giáo viên (teacher_profiles.full_name); chỉ điền ở danh sách dành cho Staff/Admin.
    private String teacherName;
    private BigDecimal totalAmount;
    private String bankInfo;
    private WithdrawalStatus status;
    private UUID approvedBy;
    private UUID paidBy;
    private String rejectedReason;
    private String transactionCode;
    private String proofImageUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
