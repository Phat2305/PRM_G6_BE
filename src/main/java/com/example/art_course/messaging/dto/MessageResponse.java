package com.example.art_course.messaging.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {

    private UUID id;
    private UUID conversationId;
    private UUID senderId;
    private String content;
    private Boolean isOffer;
    private OffsetDateTime createdAt;
    // Chỉ có ở tin báo giá: trạng thái hiện tại của báo giá (pending/accepted/superseded/rejected/cancelled).
    private UUID offerId;
    private String offerStatus;
    private java.math.BigDecimal offerPrice;
    private Integer deliveryDays;
}