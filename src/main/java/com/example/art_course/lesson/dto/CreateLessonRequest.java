package com.example.art_course.lesson.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateLessonRequest {

    @JsonAlias({"courseId", "course_id"})
    private UUID courseId;

    @JsonAlias({"supplyId", "supply_id"})
    private UUID supplyId;

    @JsonAlias({"title", "name"})
    private String title;

    @JsonAlias({"videoUrl", "video_url", "url"})
    private String videoUrl;

    @JsonAlias({"isTrial", "is_trial", "trial"})
    private Boolean isTrial;

    @JsonAlias({"hasAssignment", "has_assignment"})
    private Boolean hasAssignment;

    private String status;
}
