
        package com.example.art_course.enrollment.controller;

import com.example.art_course.enrollment.dto.CreateEnrollmentRequest;
import com.example.art_course.enrollment.dto.EnrollmentResponse;
import com.example.art_course.enrollment.dto.UpdateEnrollmentStatusRequest;
import com.example.art_course.enrollment.service.EnrollmentService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(
            EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> create(
            @Valid @RequestBody CreateEnrollmentRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrollmentService.create(request));
    }

    @GetMapping("/child/{childId}")
    public ResponseEntity<List<EnrollmentResponse>> getByChildId(
            @PathVariable UUID childId) {

        return ResponseEntity.ok(
                enrollmentService.getByChildId(childId));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<EnrollmentResponse>> getByCourseId(
            @PathVariable UUID courseId) {

        return ResponseEntity.ok(
                enrollmentService.getByCourseId(courseId));
    }

    @GetMapping("/child/{childId}/course/{courseId}")
    public ResponseEntity<EnrollmentResponse> getById(
            @PathVariable UUID childId,
            @PathVariable UUID courseId) {

        return ResponseEntity.ok(
                enrollmentService.getById(childId, courseId));
    }

    @PatchMapping("/child/{childId}/course/{courseId}/status")
    public ResponseEntity<EnrollmentResponse> updateStatus(
            @PathVariable UUID childId,
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateEnrollmentStatusRequest request) {

        return ResponseEntity.ok(
                enrollmentService.updateStatus(
                        childId, courseId, request));
    }
}
