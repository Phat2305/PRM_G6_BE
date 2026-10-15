package com.example.art_course.modules.withdrawal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectWithdrawalRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    private String reason;
}
