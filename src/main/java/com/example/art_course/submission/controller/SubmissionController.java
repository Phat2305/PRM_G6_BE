
package com.example.art_course.submission.controller;

import com.example.art_course.submission.dto.GradeSubmissionRequest;
import com.example.art_course.submission.dto.SubmissionResponse;
import com.example.art_course.submission.service.SubmissionService;

import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponse> upload(
            @RequestParam("lessonId") UUID lessonId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissionService.upload(
                        lessonId, file, authentication.getName()));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(
            @PathVariable UUID id,
            Authentication authentication) {

        ResponseInputStream<GetObjectResponse> object =
                submissionService.image(id, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.response().contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .body(new InputStreamResource(object));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResponse> getById(
            @PathVariable UUID id,
            Authentication authentication) {

        return ResponseEntity.ok(
                submissionService.getById(id, authentication.getName()));
    }

    @GetMapping("/child/{childId}")
    public ResponseEntity<List<SubmissionResponse>> getByChildId(
            @PathVariable UUID childId,
            Authentication authentication) {

        return ResponseEntity.ok(
                submissionService.getByChildId(
                        childId, authentication.getName()));
    }

    @GetMapping("/lesson/{lessonId}")
    public ResponseEntity<List<SubmissionResponse>> getByLessonId(
            @PathVariable UUID lessonId,
            Authentication authentication) {

        return ResponseEntity.ok(
                submissionService.getByLessonId(
                        lessonId, authentication.getName()));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<SubmissionResponse>> getByStatus(
            @PathVariable String status,
            Authentication authentication) {

        return ResponseEntity.ok(
                submissionService.getByStatus(
                        status, authentication.getName()));
    }

    @PutMapping("/{id}/grade")
    public ResponseEntity<SubmissionResponse> grade(
            @PathVariable UUID id,
            @Valid @RequestBody GradeSubmissionRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                submissionService.grade(
                        id, request, authentication.getName()));
    }
}
