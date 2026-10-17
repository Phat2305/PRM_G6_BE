
        package com.example.art_course.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateCourseReviewRequest {

    @NotNull
    @Min(1)
    @Max(5)
    private Short rating;

    private String comment;
}
