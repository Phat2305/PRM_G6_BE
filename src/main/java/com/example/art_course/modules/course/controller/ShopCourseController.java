package com.example.art_course.modules.course.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.course.dto.CourseDetailDto;
import com.example.art_course.modules.course.dto.CourseSummaryDto;
import com.example.art_course.modules.course.dto.TrialLessonDto;
import com.example.art_course.modules.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint thương mại cho Khóa học (SHOP-01, SHOP-10).
 * Tách biệt hoàn toàn khỏi /api/v1/courses của BE1 để tránh trùng route.
 * Tuyệt đối không trả lessons.video_url thô (SHOP-10 cấp qua IVideoAccessContract).
 */
@RestController
@RequestMapping("/api/v1/shop/courses")
@RequiredArgsConstructor
public class ShopCourseController {

    private final CourseService courseService;

    // Không truyền size thì trả cả danh sách như cũ. Có size: ?page=0&size=10&q=...&category=...;
    // tổng số và cờ còn trang sau nằm ở header X-Total-Count, X-Has-More.
    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseSummaryDto>>> listPublishedCourses(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return courseService.listCourses(q, category, page, size).toResponse();
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailDto> getCourseDetail(@PathVariable UUID courseId) {
        return ApiResponse.ok(courseService.getCourseDetail(courseId));
    }

    @GetMapping("/lessons/{lessonId}/trial")
    public ApiResponse<TrialLessonDto> getFreeTrialLesson(@PathVariable UUID lessonId) {
        return ApiResponse.ok(courseService.getFreeTrialLesson(lessonId));
    }
}
