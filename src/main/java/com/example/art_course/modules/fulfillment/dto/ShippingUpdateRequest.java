package com.example.art_course.modules.fulfillment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingUpdateRequest {

    @NotBlank(message = "Đơn vị vận chuyển không được để trống")
    @JsonAlias({"shippingProvider", "shipping_provider", "carrier", "provider"})
    private String shippingProvider;

    @NotBlank(message = "Mã vận đơn không được để trống")
    @JsonAlias({"trackingCode", "tracking_code", "trackingNumber", "tracking_number", "code"})
    private String trackingCode;
}
