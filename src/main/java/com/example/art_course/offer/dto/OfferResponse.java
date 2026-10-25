
        package com.example.art_course.offer.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferResponse {

    private UUID id;
    private UUID requestId;
    private UUID teacherId;
    private BigDecimal price;
    private String message;
    private String status;
    private Integer deliveryDays;
    // Tin báo giá vừa tạo trong hội thoại (null nếu không gửi kèm hội thoại).
    private UUID messageId;
}