package com.example.art_course.modules.fulfillment.dto;

import com.example.art_course.common.statemachine.DeliveryStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStatusResponseDto {
    private UUID orderDetailId;
    private UUID orderId;
    private String productName;
    private DeliveryStatus deliveryStatus;
    private String shippingProvider;
    private String trackingCode;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant confirmedDeliveredAt;
}
