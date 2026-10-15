package com.example.art_course.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {

    @NotNull(message = "Mã đơn hàng không được để trống")
    private UUID orderId;

    @NotBlank(message = "Cổng thanh toán không được để trống")
    private String gateway; // VNPAY, MOMO, MOCK

    @NotBlank(message = "Return URL không được để trống")
    private String returnUrl;
}
