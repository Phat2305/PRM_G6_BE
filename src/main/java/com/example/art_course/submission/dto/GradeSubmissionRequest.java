
        package com.example.art_course.submission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GradeSubmissionRequest {

    @NotBlank
    private String status;

    private String teacherFeedback;

    private String badge;
}
