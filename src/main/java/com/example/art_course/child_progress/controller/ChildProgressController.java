
        package com.example.art_course.child_progress.controller;

import com.example.art_course.child_progress.dto.ChildProgressResponse;
import com.example.art_course.child_progress.dto.UpdateProgressRequest;
import com.example.art_course.child_progress.service.ChildProgressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
            @PathVariable UUID childId) {

        return ResponseEntity.ok(
                progressService.getByChildId(childId));
    }

    @GetMapping("/child/{childId}/lesson/{lessonId}")
    public ResponseEntity<ChildProgressResponse> getProgress(
            @PathVariable UUID childId,
            @PathVariable UUID lessonId) {

        return ResponseEntity.ok(
                progressService.getProgress(childId, lessonId));
    }

    @PutMapping("/child/{childId}/lesson/{lessonId}")
    public ResponseEntity<ChildProgressResponse> updateProgress(
            @PathVariable UUID childId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody UpdateProgressRequest request) {

        return ResponseEntity.ok(
                progressService.updateProgress(
                        childId, lessonId, request));
    }
}