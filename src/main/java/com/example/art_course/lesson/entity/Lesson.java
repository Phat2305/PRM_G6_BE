
package com.example.art_course.lesson.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "lessons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "supply_id")
    private UUID supplyId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "video_url", columnDefinition = "text")
    private String videoUrl;

    @Column(name = "is_trial")
    private Boolean isTrial;

    @Column(name = "has_assignment")
    private Boolean hasAssignment;

    @Column(name = "status", length = 20)
    private String status;
}
