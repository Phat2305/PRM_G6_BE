package com.example.art_course.enrollment.controller;

import com.example.art_course.common.AppError;
import com.example.art_course.enrollment.dto.EnrollmentResponse;
import com.example.art_course.enrollment.dto.UpdateEnrollmentStatusRequest;
import com.example.art_course.enrollment.service.EnrollmentService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Quyền học chỉ được cấp khi thanh toán thành công (EnrollmentGrantService), nên không có endpoint tạo.
@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(
            EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    // Bé xem của mình, hoặc phụ huynh của bé.
    @GetMapping("/child/{childId}")
    public ResponseEntity<List<EnrollmentResponse>> getByChildId(
            @PathVariable UUID childId,
            Authentication authentication) {

        return ResponseEntity.ok(
                enrollmentService.getByChildId(callerId(authentication), childId));
    }

    // Chỉ giáo viên sở hữu khóa.
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<EnrollmentResponse>> getByCourseId(
            @PathVariable UUID courseId,
            Authentication authentication) {

        return ResponseEntity.ok(
                enrollmentService.getByCourseId(callerId(authentication), courseId));
    }

    @GetMapping("/child/{childId}/course/{courseId}")
    public ResponseEntity<EnrollmentResponse> getById(
            @PathVariable UUID childId,
            @PathVariable UUID courseId,
            Authentication authentication) {

        return ResponseEntity.ok(
                enrollmentService.getById(callerId(authentication), childId, courseId));
    }

    // Thu hồi hoặc khôi phục quyền học thủ công: chỉ Staff/Admin.
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PatchMapping("/child/{childId}/course/{courseId}/status")
    public ResponseEntity<EnrollmentResponse> updateStatus(
            @PathVariable UUID childId,
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateEnrollmentStatusRequest request) {

        return ResponseEntity.ok(
                enrollmentService.updateStatus(
                        childId, courseId, request));
    }

    // Bộ lọc JWT đặt id người dùng (UUID) làm principal.
    private UUID callerId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID id)) {
            throw new AppError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token không hợp lệ");
        }
        return id;
    }
}
