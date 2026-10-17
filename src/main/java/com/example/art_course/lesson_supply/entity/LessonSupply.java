
package com.example.art_course.lesson_supply.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "lesson_supplies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonSupply {

    @EmbeddedId
    private LessonSupplyId id;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;
}
