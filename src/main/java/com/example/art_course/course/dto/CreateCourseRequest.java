
package com.example.art_course.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreateCourseRequest {

    @com.fasterxml.jackson.annotation.JsonAlias({"teacher_id"})
    private UUID teacherId;

    @com.fasterxml.jackson.annotation.JsonAlias({"customize_request_id"})
    private UUID customizeRequestId;

    @com.fasterxml.jackson.annotation.JsonAlias({"title", "courseName", "course_name"})
    @NotBlank(message = "Course name is required")
    @Size(max = 255)
    private String name;

    private String description;

    @Size(max = 100)
    private String category;

    @com.fasterxml.jackson.annotation.JsonAlias({"cover_image_url", "thumbnailUrl", "imageUrl", "thumbnail"})
    private String coverImageUrl;

    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    private BigDecimal price;

    @Size(max = 20)
    private String status;
}
