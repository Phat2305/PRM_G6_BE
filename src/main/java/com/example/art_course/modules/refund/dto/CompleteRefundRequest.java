package com.example.art_course.modules.refund.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteRefundRequest {

    @NotBlank(message = "Mã giao dịch hoàn tiền không được để trống")
    @JsonAlias({"transactionCode", "transaction_code", "txnCode", "txn_code"})
    private String transactionCode;

    @JsonAlias({"proofImageUrl", "proof_image_url", "proofUrl", "proof_url", "imageUrl", "image_url"})
    private String proofImageUrl;
}
