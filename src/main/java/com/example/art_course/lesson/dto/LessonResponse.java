
package com.example.art_course.lesson.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LessonResponse {

    private UUID id;

    private UUID courseId;

    private UUID supplyId;

    private String title;

    private String videoUrl;

    private Boolean isTrial;

    private Boolean hasAssignment;

    private String status;

    private String videoStatus;
    // Do server đo từ video đã xử lý; client không đặt được (dùng để kiểm "đã học xong").
    private Integer durationSeconds;
    private String assignmentTitle;
    private String assignmentPrompt;
}
