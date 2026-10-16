        package com.example.art_course.refund.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateRefundRequest {

    @NotBlank
    private String status;

    private String bankInfo;
}
