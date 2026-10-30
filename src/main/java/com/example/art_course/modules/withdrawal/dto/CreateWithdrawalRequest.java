package com.example.art_course.modules.withdrawal.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWithdrawalRequest {

    @NotNull(message = "Số tiền rút không được để trống")
    @DecimalMin(value = "10000", message = "Số tiền rút tối thiểu là 10.000 VND")
    @JsonAlias({"amount", "money", "totalAmount", "total_amount"})
    private BigDecimal amount;

    @NotBlank(message = "Thông tin tài khoản nhận tiền không được để trống")
    @JsonAlias({"bankInfo", "bank_info", "bankAccount", "bank_account", "accountInfo", "account_info"})
    private String bankInfo;
}
