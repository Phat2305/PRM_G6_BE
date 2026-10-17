package com.example.art_course.modules.payment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"orderId", "order_id"})
    private UUID orderId;

    @NotBlank(message = "Cổng thanh toán không được để trống")
    @JsonAlias({"gateway", "paymentGateway", "payment_gateway", "paymentMethod", "payment_method"})
    private String gateway; // VNPAY, MOMO, MOCK

    @NotBlank(message = "Return URL không được để trống")
    @JsonAlias({"returnUrl", "return_url", "redirectUrl", "redirect_url"})
    private String returnUrl;
}
