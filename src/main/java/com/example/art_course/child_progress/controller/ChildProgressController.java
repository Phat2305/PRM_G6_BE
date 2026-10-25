package com.example.art_course.child_progress.controller;

import com.example.art_course.child_progress.dto.ChildProgressResponse;
import com.example.art_course.child_progress.dto.CourseProgressResponse;
import com.example.art_course.child_progress.dto.UpdateProgressRequest;
import com.example.art_course.child_progress.service.ChildProgressService;
import com.example.art_course.common.AppError;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/child-progress")
public class ChildProgressController {

    private final ChildProgressService progressService;

    public ChildProgressController(
            ChildProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/child/{childId}")
    public ResponseEntity<List<ChildProgressResponse>> getByChildId(
            @PathVariable UUID childId,
            Authentication authentication) {

        return ResponseEntity.ok(
                progressService.getByChildId(callerId(authentication), childId));
    }

    @GetMapping("/child/{childId}/lesson/{lessonId}")
    public ResponseEntity<ChildProgressResponse> getProgress(
            @PathVariable UUID childId,
            @PathVariable UUID lessonId,
            Authentication authentication) {

        return ResponseEntity.ok(
                progressService.getProgress(callerId(authentication), childId, lessonId));
    }

    @GetMapping("/child/{childId}/course/{courseId}")
    public ResponseEntity<CourseProgressResponse> getCourseProgress(
            @PathVariable UUID childId,
            @PathVariable UUID courseId,
            Authentication authentication) {

        return ResponseEntity.ok(
                progressService.getCourseProgress(callerId(authentication), childId, courseId));
    }

    // Teacher of the course: all enrolled children with their progress.
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<CourseProgressResponse>> getCourseRoster(
            @PathVariable UUID courseId,
            Authentication authentication) {

        return ResponseEntity.ok(
                progressService.getCourseRoster(callerId(authentication), courseId));
    }

    @PutMapping("/child/{childId}/lesson/{lessonId}")
    public ResponseEntity<ChildProgressResponse> updateProgress(
            @PathVariable UUID childId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody UpdateProgressRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                progressService.updateProgress(
                        callerId(authentication), childId, lessonId, request));
    }

    // The JWT filter puts the user id (UUID) in the principal.
    private UUID callerId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID id)) {
            throw new AppError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token không hợp lệ");
        }
        return id;
    }
}
