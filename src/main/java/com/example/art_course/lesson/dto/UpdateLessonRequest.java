
package com.example.art_course.lesson.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateLessonRequest {

    @Size(max = 255)
    private String title;

    private String videoUrl;

    private Boolean isTrial;

    private Boolean hasAssignment;

    private String status;
}
