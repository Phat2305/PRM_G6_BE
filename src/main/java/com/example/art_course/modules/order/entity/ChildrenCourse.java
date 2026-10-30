package com.example.art_course.modules.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "children_course")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChildrenCourse {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(name = "price", nullable = false)
    private BigDecimal price;
}
