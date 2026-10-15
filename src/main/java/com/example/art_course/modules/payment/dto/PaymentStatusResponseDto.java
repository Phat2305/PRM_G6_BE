package com.example.art_course.modules.payment.dto;

import com.example.art_course.common.statemachine.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStatusResponseDto {
    private UUID paymentId;
    private UUID orderId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String gatewayTransactionId;
    private Instant paidAt;
}
