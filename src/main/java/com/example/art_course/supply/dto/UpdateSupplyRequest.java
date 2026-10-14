
package com.example.art_course.supply.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateSupplyRequest {

    @Size(max = 255)
    private String name;

    private String description;

    private String imageUrl;

    @DecimalMin("0.0")
    private BigDecimal price;

    @Min(0)
    private Integer stockQuantity;

    @Size(max = 20)
    private String status;
}
