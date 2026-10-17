
package com.example.art_course.review.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseReviewResponse {

    private UUID id;
    private UUID courseId;
    private UUID childId;
    private UUID parentId;
    private Short rating;
    private String comment;
}
