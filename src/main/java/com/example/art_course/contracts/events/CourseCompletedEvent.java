package com.example.art_course.contracts.events;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Sự kiện phát sinh khi Child hoàn thành khóa học (LRN-07 / BE1).
 * Kích hoạt giải phóng tiền escrow cho dòng khóa học (mục 7.1).
 */
public class CourseCompletedEvent extends ApplicationEvent {
    private final UUID courseId;
    private final UUID childId;

    public CourseCompletedEvent(Object source, UUID courseId, UUID childId) {
        super(source);
        this.courseId = courseId;
        this.childId = childId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getChildId() {
        return childId;
    }
}
