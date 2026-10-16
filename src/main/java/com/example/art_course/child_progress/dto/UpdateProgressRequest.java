package com.example.art_course.child_progress.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProgressRequest {

    @NotNull
    @Min(0)
    private Integer watchedSeconds;

    private Boolean isCompleted;
}
