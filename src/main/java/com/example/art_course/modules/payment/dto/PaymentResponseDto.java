package com.example.art_course.modules.payment.dto;

import com.example.art_course.common.statemachine.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDto {
    private UUID paymentId;
    private UUID orderId;
    private String gateway;
    private BigDecimal amount;
    private String paymentUrl;
    private PaymentStatus status;
}
