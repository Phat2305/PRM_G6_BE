package com.example.art_course.contracts.events;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Sự kiện phát sinh khi Parent đánh giá khóa học (SHOP-09).
 * Kích hoạt giải phóng tiền escrow cho dòng khóa học (mục 7.1).
 */
public class CourseReviewedEvent extends ApplicationEvent {
    private final UUID courseId;
    private final UUID parentId;
    private final UUID childId;
    private final int rating;
    private final String comment;

    public CourseReviewedEvent(Object source, UUID courseId, UUID parentId, UUID childId, int rating, String comment) {
        super(source);
        this.courseId = courseId;
        this.parentId = parentId;
        this.childId = childId;
        this.rating = rating;
        this.comment = comment;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public UUID getChildId() {
        return childId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }
}
