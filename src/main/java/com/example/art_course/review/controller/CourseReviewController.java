
        package com.example.art_course.review.controller;

import com.example.art_course.review.dto.CourseReviewResponse;
import com.example.art_course.review.dto.CreateCourseReviewRequest;
import com.example.art_course.review.dto.UpdateCourseReviewRequest;
import com.example.art_course.review.service.CourseReviewService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/course-reviews")
public class CourseReviewController {

    private final CourseReviewService service;

    public CourseReviewController(CourseReviewService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CourseReviewResponse> create(
            @Valid @RequestBody CreateCourseReviewRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseReviewResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<CourseReviewResponse>> getByCourseId(
            @PathVariable UUID courseId) {

        return ResponseEntity.ok(service.getByCourseId(courseId));
    }

    @GetMapping("/child/{childId}")
    public ResponseEntity<List<CourseReviewResponse>> getByChildId(
            @PathVariable UUID childId) {

        return ResponseEntity.ok(service.getByChildId(childId));
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<CourseReviewResponse>> getByParentId(
            @PathVariable UUID parentId) {

        return ResponseEntity.ok(service.getByParentId(parentId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseReviewResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseReviewRequest request) {

        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
