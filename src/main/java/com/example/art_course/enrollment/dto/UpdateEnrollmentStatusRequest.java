package com.example.art_course.enrollment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateEnrollmentStatusRequest {

    @NotBlank
    @JsonAlias({"status", "enrollmentStatus", "state"})
    private String status;
}
