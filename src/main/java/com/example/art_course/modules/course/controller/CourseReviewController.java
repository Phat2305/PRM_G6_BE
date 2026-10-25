package com.example.art_course.modules.course.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.course.entity.CourseReview;
import com.example.art_course.modules.course.service.CourseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý đánh giá khóa học (SHOP-09).
 * Đường dẫn riêng biệt /api/v1/course-reviews, không đụng tới route /api/v1/courses của BE1.
 */
@RestController("be2CourseReviewController")
@RequestMapping("/api/v1/course-reviews")
@RequiredArgsConstructor
public class CourseReviewController {

    private final CourseService courseService;

    // Phụ huynh xem được đánh giá của mọi khóa đang bán; giáo viên chỉ xem khóa của mình.
    @GetMapping("/course/{courseId}")
    public ApiResponse<List<ReviewView>> getReviews(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal UUID callerId,
            Authentication authentication
    ) {
        boolean teacher = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TEACHER".equals(a.getAuthority()));
        List<CourseReview> reviews = teacher
                ? courseService.getCourseReviewsForTeacher(courseId, callerId)
                : courseService.getCourseReviews(courseId);
        return ApiResponse.ok(reviews.stream()
                .map(review -> ReviewView.of(review, callerId))
                .toList());
    }

    @PostMapping("/course/{courseId}")
    public ApiResponse<ReviewView> createReview(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal UUID parentId,
            @RequestBody CreateReviewRequest request
    ) {
        CourseReview review = courseService.createCourseReview(
                courseId, parentId, request.getRating(), request.getComment()
        );
        return ApiResponse.ok("Gửi đánh giá thành công (SHOP-09)", ReviewView.of(review, parentId));
    }

    /**
     * Không trả parentId/childId của người khác (mã tài khoản của phụ huynh và bé);
     * `mine` cho biết đây có phải đánh giá của người đang xem không.
     */
    public record ReviewView(UUID id, int rating, String comment, Instant createdAt, boolean mine) {
        static ReviewView of(CourseReview review, UUID callerId) {
            return new ReviewView(review.getId(), review.getRating(), review.getComment(),
                    review.getCreatedAt(), review.getParentId().equals(callerId));
        }
    }

    @Data
    public static class CreateReviewRequest {
        private int rating;
        private String comment;
    }
}
