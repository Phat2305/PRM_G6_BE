
        package com.example.art_course.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateModerationReviewRequest {

    @NotNull
    private UUID staffId;

    @NotBlank
    @Size(max = 50)
    private String targetType;

    @NotNull
    private UUID targetId;

    @Size(max = 100)
    private String decision;

    private String reason;
}
