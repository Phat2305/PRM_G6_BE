
package com.example.art_course.supply.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreateSupplyRequest {

    @NotNull
    private UUID teacherId;

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    private String imageUrl;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal price;

    @NotNull
    @Min(0)
    private Integer stockQuantity;

    @Size(max = 20)
    private String status;
}
