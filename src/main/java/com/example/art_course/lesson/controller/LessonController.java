
package com.example.art_course.lesson.controller;

import com.example.art_course.lesson.dto.CreateLessonRequest;
import com.example.art_course.lesson.dto.LessonResponse;
import com.example.art_course.lesson.dto.UpdateLessonRequest;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.service.LessonService;
import com.example.art_course.course.service.CourseService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonService lessonService;
    private final CourseService courseService;

    public LessonController(LessonService lessonService, CourseService courseService) {
        this.lessonService = lessonService;
        this.courseService = courseService;
    }

    @GetMapping("/course/{courseId}")
    public List<LessonResponse> getLessonsByCourseId(
            @PathVariable UUID courseId, Authentication authentication) {

        courseService.getVisibleCourseById(courseId, authentication.getName());

        return lessonService.getLessonsByCourseId(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{lessonId}")
    public LessonResponse getLessonById(
            @PathVariable UUID lessonId, Authentication authentication) {
        Lesson lesson = lessonService.getLessonById(lessonId);
        courseService.getVisibleCourseById(lesson.getCourseId(), authentication.getName());
        return toResponse(lesson);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LessonResponse createLesson(
            @Valid @RequestBody CreateLessonRequest request,
            Authentication authentication) {

        Lesson lesson = Lesson.builder()
                .courseId(request.getCourseId())
                .supplyId(request.getSupplyId())
                .title(request.getTitle())
                .videoUrl(request.getVideoUrl())
                .isTrial(request.getIsTrial())
                .hasAssignment(request.getHasAssignment())
                .assignmentTitle(request.getAssignmentTitle())
                .assignmentPrompt(request.getAssignmentPrompt())
                .status(request.getStatus())
                .build();

        Lesson savedLesson = lessonService.createLesson(
                lesson,
                authentication.getName()
        );

        return toResponse(savedLesson);
    }

    @PutMapping("/{lessonId}")
    public LessonResponse updateLesson(
            @PathVariable UUID lessonId,
            @Valid @RequestBody UpdateLessonRequest request,
            Authentication authentication) {

        return toResponse(
                lessonService.updateLesson(
                        lessonId,
                        request,
                        authentication.getName()
                )
        );
    }

    @DeleteMapping("/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(
            @PathVariable UUID lessonId,
            Authentication authentication) {

        lessonService.deleteLesson(
                lessonId,
                authentication.getName()
        );
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
                lesson.getStatus(),
                lesson.getVideoStatus(),
                lesson.getDurationSeconds(),
                lesson.getAssignmentTitle(),
                lesson.getAssignmentPrompt()
        );
    }
}
