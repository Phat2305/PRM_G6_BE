
        package com.example.art_course.customize_request.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "customize_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomizeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "child_age")
    private Short childAge;

    @Column(name = "budget", precision = 12, scale = 2)
    private BigDecimal budget;

    @Column(name = "deadline")
    private OffsetDateTime deadline;

    @Column(name = "status", length = 20)
    private String status;

    // Số lần Staff từ chối trong lượt duyệt hiện tại (đặt lại về 0 khi được duyệt) và lý do gần nhất.
    @Column(name = "rejection_count", nullable = false)
    @Builder.Default
    private int rejectionCount = 0;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;
}
