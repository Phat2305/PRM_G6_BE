package com.example.art_course.modules.cart.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "product_type", nullable = false, length = 20)
    private String productType; // COURSE, SUPPLY

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "supply_id")
    private UUID supplyId;

    @Column(name = "child_id")
    private UUID childId;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
