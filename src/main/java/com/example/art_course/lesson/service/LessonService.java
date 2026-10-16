
package com.example.art_course.lesson.service;

import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.dto.UpdateLessonRequest;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class LessonService {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public LessonService(
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            UserRepository userRepository) {

        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Lesson> getLessonsByCourseId(UUID courseId) {
        if (courseId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Course ID is required"
            );
        }

        return lessonRepository.findAllByCourseId(courseId);
    }

    @Transactional(readOnly = true)
    public Lesson getLessonById(UUID lessonId) {
        if (lessonId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lesson ID is required"
            );
        }

        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lesson not found: " + lessonId
                ));
    }

    @Transactional
    public Lesson createLesson(Lesson lesson, String username) {
        if (lesson == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lesson data is required"
            );
        }

        if (lesson.getCourseId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Course ID is required"
            );
        }

        if (lesson.getTitle() == null
                || lesson.getTitle().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lesson title is required"
            );
        }

        Course course = getCourseById(lesson.getCourseId());
        User user = getUserByUsername(username);

        checkCourseOwnership(course, user);

        lesson.setId(null);

        return lessonRepository.save(lesson);
    }

    @Transactional
    public Lesson updateLesson(
            UUID lessonId,
            UpdateLessonRequest request,
            String username) {

        Lesson lesson = getLessonById(lessonId);

        Course course = getCourseById(lesson.getCourseId());
        User user = getUserByUsername(username);

        checkCourseOwnership(course, user);

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Lesson title cannot be blank"
                );
            }

            lesson.setTitle(request.getTitle().trim());
        }

        if (request.getVideoUrl() != null) {
            lesson.setVideoUrl(request.getVideoUrl());
        }

        if (request.getIsTrial() != null) {
            lesson.setIsTrial(request.getIsTrial());
        }

        if (request.getHasAssignment() != null) {
            lesson.setHasAssignment(request.getHasAssignment());
        }

        if (request.getStatus() != null) {
            lesson.setStatus(request.getStatus());
        }

        return lessonRepository.save(lesson);
    }

    @Transactional
    public void deleteLesson(UUID lessonId, String username) {
        Lesson lesson = getLessonById(lessonId);

        Course course = getCourseById(lesson.getCourseId());
        User user = getUserByUsername(username);

        checkCourseOwnership(course, user);

        lessonRepository.delete(lesson);
    }

    private Course getCourseById(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Course not found: " + courseId
                ));
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));
    }

    private void checkCourseOwnership(Course course, User user) {
        if (course.getTeacherId() == null
                || !course.getTeacherId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to manage lessons in this course"
            );
        }
    }
}
