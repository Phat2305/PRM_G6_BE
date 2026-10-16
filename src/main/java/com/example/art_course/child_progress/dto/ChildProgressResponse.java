
package com.example.art_course.child_progress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChildProgressResponse {

    private UUID childId;
    private UUID lessonId;
    private Integer watchedSeconds;
    private Boolean isCompleted;
}
