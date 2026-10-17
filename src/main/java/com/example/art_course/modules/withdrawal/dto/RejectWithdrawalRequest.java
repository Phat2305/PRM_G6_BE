package com.example.art_course.modules.withdrawal.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectWithdrawalRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    @JsonAlias({"reason", "rejectReason", "reject_reason", "note"})
    private String reason;
}
