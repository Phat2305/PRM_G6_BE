package com.example.art_course.enrollment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"childId", "child_id"})
    private UUID childId;

    @NotNull
    @JsonAlias({"courseId", "course_id"})
    private UUID courseId;
}
