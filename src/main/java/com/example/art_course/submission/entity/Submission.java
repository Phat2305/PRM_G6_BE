
package com.example.art_course.submission.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "submitted";

    @Column(name = "teacher_feedback", columnDefinition = "text")
    private String teacherFeedback;

    @Column(name = "badge", length = 100)
    private String badge;


}
