package com.example.art_course.moderation.controller;

import com.example.art_course.moderation.dto.CreateModerationReviewRequest;
import com.example.art_course.moderation.dto.ModerationReviewResponse;
import com.example.art_course.moderation.service.ModerationReviewService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moderation-reviews")
public class ModerationReviewController {

    private final ModerationReviewService service;

    public ModerationReviewController(ModerationReviewService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ModerationReviewResponse> create(
            @Valid @RequestBody CreateModerationReviewRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModerationReviewResponse> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getById(id, authentication.getName()));
    }

    @GetMapping("/staff/{staffId}")
    public ResponseEntity<List<ModerationReviewResponse>> getByStaffId(
            @PathVariable UUID staffId,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getByStaffId(staffId, authentication.getName()));
    }

    @GetMapping("/target")
    public ResponseEntity<List<ModerationReviewResponse>> getByTarget(
            @RequestParam String targetType,
            @RequestParam UUID targetId,
            Authentication authentication) {
        return ResponseEntity.ok(service.getByTarget(
                targetType, targetId, authentication.getName()));
    }

    @GetMapping("/target-type/{targetType}")
    public ResponseEntity<List<ModerationReviewResponse>> getByTargetType(
            @PathVariable String targetType,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getByTargetType(targetType, authentication.getName()));
    }

    @GetMapping("/decision/{decision}")
    public ResponseEntity<List<ModerationReviewResponse>> getByDecision(
            @PathVariable String decision,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getByDecision(decision, authentication.getName()));
    }
}