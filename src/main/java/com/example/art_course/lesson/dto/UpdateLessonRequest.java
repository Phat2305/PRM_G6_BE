package com.example.art_course.lesson.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateLessonRequest {

    @Size(max = 255)
    @JsonAlias({"title", "name"})
    private String title;

    @JsonAlias({"videoUrl", "video_url", "url"})
    private String videoUrl;

    @JsonAlias({"isTrial", "is_trial", "trial"})
    private Boolean isTrial;

    @JsonAlias({"hasAssignment", "has_assignment"})
    private Boolean hasAssignment;

    @jakarta.validation.constraints.Size(max = 255)
    private String assignmentTitle;

    @jakarta.validation.constraints.Size(max = 2000)
    private String assignmentPrompt;

    private String status;
}
