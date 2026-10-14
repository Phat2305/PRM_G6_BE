
package com.example.art_course.lesson.controller;

import com.example.art_course.lesson.dto.CreateLessonRequest;
import com.example.art_course.lesson.dto.LessonResponse;
import com.example.art_course.lesson.dto.UpdateLessonRequest;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.service.LessonService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping("/course/{courseId}")
    public List<LessonResponse> getLessonsByCourseId(
            @PathVariable UUID courseId) {

        return lessonService.getLessonsByCourseId(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{lessonId}")
    public LessonResponse getLessonById(
            @PathVariable UUID lessonId) {

        return toResponse(
                lessonService.getLessonById(lessonId)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LessonResponse createLesson(
            @Valid @RequestBody CreateLessonRequest request) {

        Lesson lesson = Lesson.builder()
                .courseId(request.getCourseId())
                .supplyId(request.getSupplyId())
                .title(request.getTitle())
                .videoUrl(request.getVideoUrl())
                .isTrial(request.getIsTrial())
                .hasAssignment(request.getHasAssignment())
                .status(request.getStatus())
                .build();

        Lesson savedLesson = lessonService.createLesson(lesson);

        return toResponse(savedLesson);
    }

    private LessonResponse toResponse(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getCourseId(),
                lesson.getSupplyId(),
                lesson.getTitle(),
                lesson.getVideoUrl(),
                lesson.getIsTrial(),
                lesson.getHasAssignment(),
                lesson.getStatus()
        );
    }

    @PutMapping("/{lessonId}")
    public LessonResponse updateLesson(
            @PathVariable UUID lessonId,
            @Valid @RequestBody UpdateLessonRequest request
    ) {
        return toResponse(
                lessonService.updateLesson(lessonId, request)
        );
    }

    @DeleteMapping("/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(
            @PathVariable UUID lessonId
    ) {
        lessonService.deleteLesson(lessonId);
    }

}
