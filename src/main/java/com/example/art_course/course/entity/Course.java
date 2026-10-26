
package com.example.art_course.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "customize_request_id")
    private UUID customizeRequestId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "cover_image_url", columnDefinition = "text")
    private String coverImageUrl;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "status", length = 20)
    private String status;

    // Số lần Staff từ chối trong lượt duyệt hiện tại (đặt lại về 0 khi được duyệt) và lý do gần nhất.
    @Column(name = "rejection_count", nullable = false)
    @Builder.Default
    private int rejectionCount = 0;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;
}
