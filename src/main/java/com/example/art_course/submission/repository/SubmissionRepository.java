
        package com.example.art_course.submission.repository;

import com.example.art_course.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubmissionRepository
        extends JpaRepository<Submission, UUID> {

    List<Submission> findAllByChildId(UUID childId);

    List<Submission> findAllByLessonId(UUID lessonId);

    List<Submission> findAllByChildIdAndLessonId(
            UUID childId,
            UUID lessonId
    );

    List<Submission> findAllByStatus(String status);
}
