
package com.example.art_course.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class UpdateCourseRequest {

    private UUID customizeRequestId;

    @Size(max = 255)
    private String name;

    private String description;

    @Size(max = 100)
    private String category;

    private String coverImageUrl;

    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    private BigDecimal price;

    @Size(max = 20)
    private String status;
}
