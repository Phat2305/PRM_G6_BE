package com.example.art_course.lesson_supply.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateLessonSupplyRequest {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @JsonAlias({"quantity", "amount", "qty"})
    private BigDecimal quantity;
}
