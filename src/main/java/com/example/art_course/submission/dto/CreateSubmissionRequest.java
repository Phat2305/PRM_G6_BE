
        package com.example.art_course.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateSubmissionRequest {

    @NotNull
    private UUID childId;

    @NotNull
    private UUID lessonId;

    @NotBlank
    private String imageUrl;
}
