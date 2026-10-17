package com.example.art_course.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

@Entity(name = "Be2Course")
@Table(name = "courses")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "customize_request_id")
    private UUID customizeRequestId;

    @Column(nullable = false)
    private String name;

    private String description;

    private String category;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, length = 20)
    private String status;
}
