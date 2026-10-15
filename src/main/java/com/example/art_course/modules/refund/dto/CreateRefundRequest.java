package com.example.art_course.modules.refund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRefundRequest {

    @NotNull(message = "Mã đơn hàng không được để trống")
    private UUID orderId;

    @NotNull(message = "Mã dòng chi tiết đơn hàng không được để trống")
    private UUID orderDetailId;

    @NotBlank(message = "Lý do hoàn tiền không được để trống")
    private String reason;

    @NotBlank(message = "Thông tin tài khoản nhận tiền hoàn không được để trống")
    private String bankInfo;
}
