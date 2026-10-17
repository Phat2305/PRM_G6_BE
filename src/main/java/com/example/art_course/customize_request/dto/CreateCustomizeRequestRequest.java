package com.example.art_course.customize_request.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateCustomizeRequestRequest {

    @JsonAlias({"parentId", "parent_id"})
    private UUID parentId;

    @NotBlank
    @Size(max = 255)
    @JsonAlias({"title", "name"})
    private String title;

    private String description;

    @Size(max = 100)
    @JsonAlias({"category", "genre"})
    private String category;

    @Min(0)
    @Max(18)
    @JsonAlias({"childAge", "child_age", "age"})
    private Short childAge;

    @DecimalMin("0.0")
    @JsonAlias({"budget", "price", "amount"})
    private BigDecimal budget;

    @JsonAlias({"deadline", "due_date", "dueDate"})
    private OffsetDateTime deadline;
}
