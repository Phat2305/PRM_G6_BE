package com.example.art_course.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "lessons")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false)
    private String title;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "is_trial", nullable = false)
    private boolean isTrial;

    @Column(name = "has_assignment", nullable = false)
    private boolean hasAssignment;

    @Column(nullable = false, length = 20)
    private String status;
}
