
        package com.example.art_course.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateEnrollmentRequest {

    @NotNull
    private UUID childId;

    @NotNull
    private UUID courseId;
}
