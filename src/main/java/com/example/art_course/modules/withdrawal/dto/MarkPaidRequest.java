package com.example.art_course.modules.withdrawal.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarkPaidRequest {

    @NotBlank(message = "Mã giao dịch chuyển khoản không được để trống")
    @JsonAlias({"transactionCode", "transaction_code", "txnCode", "txn_code"})
    private String transactionCode;

    @JsonAlias({"proofImageUrl", "proof_image_url", "proofUrl", "proof_url", "imageUrl", "image_url"})
    private String proofImageUrl;
}
