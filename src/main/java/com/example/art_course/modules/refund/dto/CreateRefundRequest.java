package com.example.art_course.modules.refund.dto;

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
public class CreateRefundRequest {

    @NotNull(message = "Mã đơn hàng không được để trống")
    @JsonAlias({"orderId", "order_id"})
    private UUID orderId;

    @NotNull(message = "Mã dòng chi tiết đơn hàng không được để trống")
    @JsonAlias({"orderDetailId", "order_detail_id", "detailId", "detail_id"})
    private UUID orderDetailId;

    @NotBlank(message = "Lý do hoàn tiền không được để trống")
    @JsonAlias({"reason", "refundReason", "refund_reason", "description"})
    private String reason;

    @NotBlank(message = "Thông tin tài khoản nhận tiền hoàn không được để trống")
    @JsonAlias({"bankInfo", "bank_info", "bankAccount", "bank_account", "accountInfo", "account_info"})
    private String bankInfo;
}
