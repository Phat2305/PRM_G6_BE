package com.example.art_course.supply.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"name", "title"})
    private String name;

    @JsonAlias({"description", "desc"})
    private String description;

    @JsonAlias({"imageUrl", "image_url"})
    private String imageUrl;

    @DecimalMin("0.0")
    @JsonAlias({"price", "cost"})
    private BigDecimal price;

    @Min(0)
    @JsonAlias({"stockQuantity", "stock_quantity", "quantity", "stock"})
    private Integer stockQuantity;

    @Size(max = 20)
    private String status;
}
