package com.example.art_course.modules.refund.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteRefundRequest {

    @NotBlank(message = "Mã giao dịch hoàn tiền không được để trống")
    private String transactionCode;

    private String proofImageUrl;
}
