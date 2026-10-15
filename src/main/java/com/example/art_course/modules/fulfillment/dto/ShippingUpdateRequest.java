package com.example.art_course.modules.fulfillment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingUpdateRequest {

    @NotBlank(message = "Đơn vị vận chuyển không được để trống")
    private String shippingProvider;

    @NotBlank(message = "Mã vận đơn không được để trống")
    private String trackingCode;
}
