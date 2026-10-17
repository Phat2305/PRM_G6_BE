package com.example.art_course.review.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"rating", "score", "stars"})
    private Short rating;

    @JsonAlias({"comment", "feedback", "review"})
    private String comment;
}
