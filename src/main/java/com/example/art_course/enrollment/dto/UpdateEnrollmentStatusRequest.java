
package com.example.art_course.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateEnrollmentStatusRequest {

    @NotBlank
    private String status;
}
