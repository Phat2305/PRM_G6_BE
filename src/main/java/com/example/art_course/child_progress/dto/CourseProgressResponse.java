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
public class CourseProgressResponse {

    private UUID childId;
    // Null khi phụ huynh chưa cho giáo viên xem tên (ASG-09).
    private String childName;
    private UUID courseId;
    private int completedLessons;
    private int totalLessons;
    // 0-100, rounded down; 0 when the course has no lessons yet.
    private int percent;
}
