package com.example.art_course.submission.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"childId", "child_id"})
    private UUID childId;

    @NotNull
    @JsonAlias({"lessonId", "lesson_id"})
    private UUID lessonId;

    @NotBlank
    @JsonAlias({"imageUrl", "image_url", "url", "fileUrl", "file_url"})
    private String imageUrl;
}
