package com.example.art_course.lesson_supply.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"lessonId", "lesson_id"})
    private UUID lessonId;

    @NotNull
    @JsonAlias({"supplyId", "supply_id"})
    private UUID supplyId;

    @DecimalMin(value = "0.0", inclusive = false)
    @JsonAlias({"quantity", "amount", "qty"})
    private BigDecimal quantity = BigDecimal.ONE;

    public BigDecimal getQuantity() {
        return quantity != null ? quantity : BigDecimal.ONE;
    }
}
