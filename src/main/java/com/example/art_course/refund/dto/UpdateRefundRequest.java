package com.example.art_course.refund.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateRefundRequest {

    @NotBlank
    @JsonAlias({"status", "refundStatus", "state"})
    private String status;

    @JsonAlias({"bankInfo", "bank_info", "bankAccount", "bank_account"})
    private String bankInfo;
}
