
        package com.example.art_course.refund.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundResponse {

    private UUID id;
    private UUID parentId;
    private BigDecimal totalAmount;
    private String status;
    private OffsetDateTime expiredAt;
    private String bankInfo;
    private OffsetDateTime updatedAt;
    private UUID paymentId;
}
