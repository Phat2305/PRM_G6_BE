package com.example.art_course.child_progress.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProgressRequest {

    // Upper bound is a sanity cap (24 hours). Completion is checked against lessons.duration_seconds in the service.
    @JsonAlias({"watched_seconds", "progressSeconds"})
    @Min(0)
    @Max(86400)
    private Integer watchedSeconds = 0;

    @JsonAlias({"is_completed", "completed"})
    private Boolean isCompleted;

    // A missing value counts as 0, so the service never sees null.
    public Integer getWatchedSeconds() {
        return watchedSeconds != null ? watchedSeconds : 0;
    }
}
