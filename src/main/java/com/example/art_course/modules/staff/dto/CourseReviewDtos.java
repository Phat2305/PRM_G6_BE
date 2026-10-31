package com.example.art_course.modules.staff.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// STF-01: khóa học chờ Staff duyệt. customize = khóa riêng làm cho một yêu cầu của phụ huynh.
public final class CourseReviewDtos {

    private CourseReviewDtos() {
    }

    public record Item(UUID id, String name, UUID teacherId, String teacherName, String category,
                       BigDecimal price, boolean customize, int rejectionCount, int lessonCount, int readyCount,
                       String coverImageUrl) {
    }

    public record LessonItem(UUID id, String title, boolean trial, boolean hasAssignment,
                             String videoStatus, String videoUrl) {
    }

    public record Detail(UUID id, String name, String description, String category, BigDecimal price,
                         String status, UUID teacherId, String teacherName, boolean customize,
                         int rejectionCount, String reviewNote, List<LessonItem> lessons, String coverImageUrl) {
    }
}
