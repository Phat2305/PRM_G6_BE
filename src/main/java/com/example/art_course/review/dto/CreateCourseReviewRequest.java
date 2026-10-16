
        package com.example.art_course.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateCourseReviewRequest {

    @NotNull
    private UUID courseId;

    @NotNull
    private UUID childId;

    @NotNull
    private UUID parentId;

    @NotNull
    @Min(1)
    @Max(5)
    private Short rating;

    private String comment;
}
