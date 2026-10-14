
package com.example.art_course.course.service;

import com.example.art_course.course.dto.CreateCourseRequest;
import com.example.art_course.course.dto.UpdateCourseRequest;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CourseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Course getCourseById(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Course ID is required"
            );
        }

        return courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Course not found"
                ));
    }

    @Transactional(readOnly = true)
    public List<Course> getCoursesByTeacher(UUID teacherId) {
        return courseRepository.findAllByTeacherId(teacherId);
    }

    @Transactional
    public Course createCourse(CreateCourseRequest request) {
        if (request.getName() == null
                || request.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Course name is required"
            );
        }

        Course course = Course.builder()
                .teacherId(request.getTeacherId())
                .customizeRequestId(request.getCustomizeRequestId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .category(request.getCategory())
                .coverImageUrl(request.getCoverImageUrl())
                .price(request.getPrice())
                .status(request.getStatus())
                .build();

        return courseRepository.save(course);
    }

    @Transactional
    public Course updateCourse(
            UUID id,
            UpdateCourseRequest request
    ) {
        Course course = getCourseById(id);

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Course name cannot be blank"
                );
            }

            course.setName(request.getName().trim());
        }

        if (request.getCustomizeRequestId() != null) {
            course.setCustomizeRequestId(
                    request.getCustomizeRequestId()
            );
        }

        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }

        if (request.getCategory() != null) {
            course.setCategory(request.getCategory());
        }

        if (request.getCoverImageUrl() != null) {
            course.setCoverImageUrl(request.getCoverImageUrl());
        }

        if (request.getPrice() != null) {
            course.setPrice(request.getPrice());
        }

        if (request.getStatus() != null) {
            course.setStatus(request.getStatus());
        }

        return courseRepository.save(course);
    }

    @Transactional
    public void deleteCourse(UUID id) {
        Course course = getCourseById(id);
        courseRepository.delete(course);
    }
}
