package com.example.art_course.modules.staff.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.staff.dto.CourseReviewDtos;
import com.example.art_course.modules.staff.dto.RejectReasonRequest;
import com.example.art_course.modules.staff.dto.RequestReviewItemDto;
import com.example.art_course.modules.staff.service.CourseModerationService;
import com.example.art_course.modules.staff.service.RequestModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * STF-01: Staff duyệt khóa học và bài đăng yêu cầu customize.
 */
@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
@RequiredArgsConstructor
public class StaffReviewController {

    private final CourseModerationService courseModeration;
    private final RequestModerationService requestModeration;

    @GetMapping("/courses/pending")
    public ApiResponse<List<CourseReviewDtos.Item>> pendingCourses() {
        return ApiResponse.ok(courseModeration.pending());
    }

    // Khóa đang công khai, để Staff gỡ khi vi phạm.
    @GetMapping("/courses/published")
    public ApiResponse<List<CourseReviewDtos.Item>> publishedCourses() {
        return ApiResponse.ok(courseModeration.published());
    }

    @GetMapping("/courses/{courseId}")
    public ApiResponse<CourseReviewDtos.Detail> courseDetail(@PathVariable UUID courseId) {
        return ApiResponse.ok(courseModeration.detail(courseId));
    }

    @PostMapping("/courses/{courseId}/approve")
    public ApiResponse<Void> approveCourse(@PathVariable UUID courseId, Authentication auth) {
        courseModeration.approve(courseId, UUID.fromString(auth.getName()));
        return ApiResponse.ok("Duyệt khóa học thành công", null);
    }

    @PostMapping("/courses/{courseId}/reject")
    public ApiResponse<Void> rejectCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody RejectReasonRequest req,
            Authentication auth) {
        courseModeration.reject(courseId, UUID.fromString(auth.getName()), req.getReason());
        return ApiResponse.ok("Từ chối khóa học thành công", null);
    }

    // STF-06: gỡ khóa công khai vi phạm (đổi status HIDDEN, lý do bắt buộc).
    @PostMapping("/courses/{courseId}/hide")
    public ApiResponse<Void> hideCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody RejectReasonRequest req,
            Authentication auth) {
        courseModeration.hide(courseId, UUID.fromString(auth.getName()), req.getReason());
        return ApiResponse.ok("Gỡ khóa học thành công", null);
    }

    @GetMapping("/customize-requests/pending")
    public ApiResponse<List<RequestReviewItemDto>> pendingRequests() {
        return ApiResponse.ok(requestModeration.pending());
    }

    @PostMapping("/customize-requests/{requestId}/approve")
    public ApiResponse<Void> approveRequest(@PathVariable UUID requestId, Authentication auth) {
        requestModeration.approve(requestId, UUID.fromString(auth.getName()));
        return ApiResponse.ok("Duyệt bài đăng thành công", null);
    }

    @PostMapping("/customize-requests/{requestId}/reject")
    public ApiResponse<Void> rejectRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectReasonRequest req,
            Authentication auth) {
        requestModeration.reject(requestId, UUID.fromString(auth.getName()), req.getReason());
        return ApiResponse.ok("Từ chối bài đăng thành công", null);
    }
}
