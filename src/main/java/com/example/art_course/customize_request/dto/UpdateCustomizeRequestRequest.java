
package com.example.art_course.customize_request.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class UpdateCustomizeRequestRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @Size(max = 100)
    private String category;

    @Min(0)
    @Max(18)
    private Short childAge;

    @DecimalMin("0.0")
    private BigDecimal budget;

    private OffsetDateTime deadline;
}
