
package com.example.art_course.lesson_supply.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class AddLessonSupplyRequest {

    @NotNull
    private UUID lessonId;

    @NotNull
    private UUID supplyId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal quantity;
}
