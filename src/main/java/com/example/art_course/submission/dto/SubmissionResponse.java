
        package com.example.art_course.submission.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionResponse {

    private UUID id;
    private UUID childId;
    private UUID lessonId;
    private String imageUrl;
    private String status;
    private String teacherFeedback;
    private String badge;
}
