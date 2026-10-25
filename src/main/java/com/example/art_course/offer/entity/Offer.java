
        package com.example.art_course.offer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "delivery_days")
    private Integer deliveryDays;

    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
