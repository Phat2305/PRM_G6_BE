
package com.example.art_course.lesson_supply.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LessonSupplyId implements Serializable {

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "supply_id", nullable = false)
    private UUID supplyId;
}
