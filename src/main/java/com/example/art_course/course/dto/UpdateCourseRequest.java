package com.example.art_course.course.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class UpdateCourseRequest {

    @JsonAlias({"customizeRequestId", "customize_request_id", "requestId", "request_id"})
    private UUID customizeRequestId;

    @Size(max = 255)
    @JsonAlias({"name", "title"})
    private String name;

    @JsonAlias({"description", "desc"})
    private String description;

    @Size(max = 100)
    @JsonAlias({"category", "genre"})
    private String category;

    @JsonAlias({"coverImageUrl", "cover_image_url", "imageUrl", "image_url"})
    private String coverImageUrl;

    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    @JsonAlias({"price", "amount", "cost"})
    private BigDecimal price;

    @Size(max = 20)
    private String status;
}
