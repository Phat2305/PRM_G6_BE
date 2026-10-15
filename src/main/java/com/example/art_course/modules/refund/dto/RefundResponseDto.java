package com.example.art_course.modules.refund.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundResponseDto {
    private UUID id;
    private UUID orderId;
    private UUID paymentId;
    private UUID parentId;
    private BigDecimal totalAmount;
    private String bankInfo;
    private String reason;
    private String status;
    private String refundTransactionCode;
    private String proofImageUrl;
    private UUID handledBy;
    private Instant createdAt;
    private Instant updatedAt;
}
