package com.example.art_course.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO tóm tắt bài học trong chi tiết khóa học.
 * Tuyệt đối không chứa videoUrl thô của bài học.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonSummaryDto {
    private UUID id;
    private String title;
    private boolean isTrial;
    private boolean hasAssignment;
    private String status;
}
