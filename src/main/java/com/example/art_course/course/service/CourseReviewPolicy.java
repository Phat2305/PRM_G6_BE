package com.example.art_course.course.service;

import com.example.art_course.course.entity.Course;
import com.example.art_course.lesson.entity.Lesson;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Quy tắc Staff duyệt khóa học (quyết định #3: khóa chỉ công khai khi Staff duyệt và mọi video READY).
 * DRAFT -> PENDING_REVIEW (giáo viên gửi duyệt) -> PUBLISHED hoặc REJECTED (Staff).
 * Bị từ chối thì sửa rồi gửi lại, tối đa MAX_REJECTIONS lần từ chối; lần thứ 3 thì không gửi lại được nữa.
 * Sửa nội dung khóa đã công khai thì khóa quay về PENDING_REVIEW (gỡ khỏi danh mục) tới khi được duyệt lại.
 */
public final class CourseReviewPolicy {

    public static final int MAX_REJECTIONS = 3;
    public static final String DRAFT = "DRAFT";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String REJECTED = "REJECTED";
    // Staff gỡ khóa đang công khai vì vi phạm (STF-06): chỉ đổi status, không xóa; giáo viên sửa rồi gửi duyệt lại.
    public static final String HIDDEN = "HIDDEN";

    private CourseReviewPolicy() {
    }

    public static boolean isCustomize(Course course) {
        return course.getCustomizeRequestId() != null;
    }

    public static boolean is(Course course, String status) {
        return status.equalsIgnoreCase(course.getStatus());
    }

    /**
     * Đã bị từ chối đủ số lần: không gửi lại được nữa. Tính theo bộ đếm chứ không theo trạng thái, để giáo viên
     * không né giới hạn bằng cách chuyển khóa về DRAFT rồi gửi lại (bộ đếm chỉ về 0 khi được duyệt).
     */
    public static boolean isFinallyRejected(Course course) {
        return course.getRejectionCount() >= MAX_REJECTIONS;
    }

    /**
     * Giáo viên vừa sửa nội dung (khóa hoặc bài học). Khóa thường đang công khai quay về chờ duyệt;
     * khóa customize đã bàn giao phải được đưa về FUNDED/DRAFT trước khi sửa.
     * Người gọi tự lưu khóa.
     */
    public static void onContentChanged(Course course) {
        if (!is(course, PUBLISHED)) {
            return;
        }
        if (isCustomize(course)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Khóa đã được bàn giao cho phụ huynh nên không sửa được nữa");
        }
        course.setStatus(PENDING_REVIEW);
    }

    /** Video xong: READY, hoặc bài cũ chưa có cột video_status nhưng đã có đường dẫn video. */
    public static boolean videoReady(Lesson lesson) {
        String status = lesson.getVideoStatus();
        if (status == null) {
            return lesson.getVideoUrl() != null && !lesson.getVideoUrl().isBlank();
        }
        return "READY".equalsIgnoreCase(status);
    }

    /** Giáo viên gửi duyệt (hoặc gửi lại sau khi bị từ chối): kiểm tra lượt gửi và nội dung khóa. */
    public static void requireSubmittable(Course course, List<Lesson> lessons) {
        if (isFinallyRejected(course)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Khóa đã bị từ chối " + MAX_REJECTIONS + " lần nên không gửi lại được");
        }
        requireReviewable(course, lessons);
    }

    /**
     * Khóa đủ điều kiện để duyệt: có tên, giá lớn hơn 0, ảnh bìa (trừ khóa customize), ít nhất một bài học và mọi video đã xử lý xong (READY).
     * Staff cũng bị kiểm tra này khi duyệt, vì video có thể đổi sau lúc gửi.
     */
    public static void requireReviewable(Course course, List<Lesson> lessons) {
        if (course.getName() == null || course.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khóa học cần có tên");
        }
        // Khóa customize bán theo báo giá của yêu cầu, không theo giá khóa.
        if (!isCustomize(course) && (course.getPrice() == null || course.getPrice().signum() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khóa học cần có giá lớn hơn 0");
        }
        // Khóa công khai cần ảnh bìa để hiện trên danh mục; khóa customize riêng cho một phụ huynh thì không.
        if (!isCustomize(course) && (course.getCoverImageUrl() == null || course.getCoverImageUrl().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khóa học cần có ảnh bìa");
        }
        if (lessons == null || lessons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khóa học cần có ít nhất một bài học");
        }
        for (Lesson lesson : lessons) {
            if (!videoReady(lesson)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Bài \"" + lesson.getTitle() + "\" chưa có video xử lý xong");
            }
        }
    }
}
