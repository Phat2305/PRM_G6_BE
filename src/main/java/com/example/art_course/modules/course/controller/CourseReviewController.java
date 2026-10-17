package com.example.art_course.modules.course.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.course.entity.CourseReview;
import com.example.art_course.modules.course.service.CourseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<CourseReview>> getReviews(@PathVariable UUID courseId) {
        return ApiResponse.ok(courseService.getCourseReviews(courseId));
    }

    @PostMapping("/course/{courseId}")
    public ApiResponse<CourseReview> createReview(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal UUID parentId,
            @RequestBody CreateReviewRequest request
    ) {
        CourseReview review = courseService.createCourseReview(
                courseId, parentId, request.getRating(), request.getComment()
        );
        return ApiResponse.ok("Gửi đánh giá thành công (SHOP-09)", review);
    }

    @Data
    public static class CreateReviewRequest {
        private int rating;
        private String comment;
    }
}
