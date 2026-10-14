
package com.example.art_course.lesson.service;

import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class LessonService {

    private final LessonRepository lessonRepository;

    public LessonService(LessonRepository lessonRepository) {
        this.lessonRepository = lessonRepository;
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
    public Lesson createLesson(Lesson lesson) {
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

        lesson.setId(null);

        return lessonRepository.save(lesson);
    }

    @Transactional
    public Lesson updateLesson(
            UUID lessonId,
            com.example.art_course.lesson.dto.UpdateLessonRequest request
    ) {
        Lesson lesson = getLessonById(lessonId);

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Lesson title cannot be blank"
                );
            }
            lesson.setTitle(request.getTitle());
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
    public void deleteLesson(UUID lessonId) {
        Lesson lesson = getLessonById(lessonId);
        lessonRepository.delete(lesson);
    }

}
