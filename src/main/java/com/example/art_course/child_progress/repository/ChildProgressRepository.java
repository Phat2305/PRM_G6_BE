
package com.example.art_course.child_progress.repository;

import com.example.art_course.child_progress.entity.ChildProgress;
import com.example.art_course.child_progress.entity.ChildProgressId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChildProgressRepository
        extends JpaRepository<ChildProgress, ChildProgressId> {

    List<ChildProgress> findAllByIdChildId(UUID childId);

    List<ChildProgress> findAllByIdLessonId(UUID lessonId);

    Optional<ChildProgress> findByIdChildIdAndIdLessonId(
            UUID childId,
            UUID lessonId
    );
}
