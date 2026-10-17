package com.example.art_course.child_progress.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProgressRequest {

    @JsonAlias({"watched_seconds", "progressSeconds", "progress"})
    @Min(0)
    private Integer watchedSeconds = 0;

    @JsonAlias({"is_completed", "completed"})
    private Boolean isCompleted;

    public Integer getWatchedSeconds() {
        return watchedSeconds != null ? watchedSeconds : 0;
    }
}
