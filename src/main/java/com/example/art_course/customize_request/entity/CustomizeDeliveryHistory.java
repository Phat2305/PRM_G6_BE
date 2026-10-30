package com.example.art_course.customize_request.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customize_delivery_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomizeDeliveryHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "lesson_count", nullable = false)
    private int lessonCount;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "result", length = 20)
    private String result;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;
}
