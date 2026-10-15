package com.example.art_course.modules.withdrawal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarkPaidRequest {

    @NotBlank(message = "Mã giao dịch chuyển khoản không được để trống")
    private String transactionCode;

    private String proofImageUrl;
}
