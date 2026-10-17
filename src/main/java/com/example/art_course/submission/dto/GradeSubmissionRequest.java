
package com.example.art_course.submission.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GradeSubmissionRequest {

    private String status;

    @JsonAlias({"feedback", "comment", "teacher_feedback"})
    private String teacherFeedback;

    private String badge;

    private Double score;

    public String getStatus() {
        if ((status == null || status.isBlank()) && score != null) {
            return score >= 5.0 ? "passed" : "not_passed";
        }
        return status != null && !status.isBlank() ? status : "passed";
    }
}
