
package com.example.art_course.course.controller;

import com.example.art_course.course.dto.CreateCourseRequest;
import com.example.art_course.course.dto.CourseResponse;
import com.example.art_course.course.dto.UpdateCourseRequest;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.service.CourseService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public List<CourseResponse> getAllCourses() {
        return courseService.getAllCourses()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public CourseResponse getCourseById(
            @PathVariable UUID id, Authentication authentication
    ) {
        return toResponse(courseService.getCourseForViewer(id, authentication.getName()));
    }

    @GetMapping("/teacher/{teacherId}")
    public List<CourseResponse> getCoursesByTeacher(
            @PathVariable UUID teacherId, Authentication authentication
    ) {
        return courseService.getCoursesForViewer(teacherId, authentication.getName())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            Authentication authentication
    ) {
        return toResponse(
                courseService.createCourse(
                        request,
                        authentication.getName()
                )
        );
    }

    @PutMapping("/{id}")
    public CourseResponse updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest request,
            Authentication authentication
    ) {
        return toResponse(
                courseService.updateCourse(
                        id,
                        request,
                        authentication.getName()
                )
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        courseService.deleteCourse(id, authentication.getName());
    }

    private CourseResponse toResponse(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getTeacherId(),
                course.getCustomizeRequestId(),
                course.getName(),
                course.getDescription(),
                course.getCategory(),
                course.getCoverImageUrl(),
                course.getPrice(),
                course.getStatus(),
                course.getRejectionCount(),
                course.getReviewNote()
        );
    }
}
