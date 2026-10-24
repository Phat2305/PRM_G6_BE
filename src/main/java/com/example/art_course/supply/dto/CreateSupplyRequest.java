
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

    @com.fasterxml.jackson.annotation.JsonAlias({"teacher_id"})
    private UUID teacherId;

    @com.fasterxml.jackson.annotation.JsonAlias({"title", "supplyName", "supply_name"})
    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @com.fasterxml.jackson.annotation.JsonAlias({"image_url", "image", "thumbnail"})
    private String imageUrl;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal price;

    @com.fasterxml.jackson.annotation.JsonAlias({"stock_quantity", "stock", "quantity"})
    @NotNull
    @Min(0)
    private Integer stockQuantity;

    // Ignored by the service: the status is decided server-side (review + stock). Kept so old clients still parse.
    @Size(max = 20)
    private String status;
}
