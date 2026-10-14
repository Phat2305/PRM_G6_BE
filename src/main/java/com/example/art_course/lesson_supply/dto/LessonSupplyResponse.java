
package com.example.art_course.lesson_supply.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class LessonSupplyResponse {

    private UUID lessonId;
    private UUID supplyId;
    private BigDecimal quantity;
}
