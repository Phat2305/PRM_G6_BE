package com.example.art_course.child_progress.repository;

import com.example.art_course.child_progress.entity.ChildProgress;
import com.example.art_course.child_progress.entity.ChildProgressId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChildProgressRepository
        extends JpaRepository<ChildProgress, ChildProgressId> {

    List<ChildProgress> findAllByIdChildId(UUID childId);

    List<ChildProgress> findAllByIdLessonId(UUID lessonId);

    List<ChildProgress> findAllByIdLessonIdIn(Collection<UUID> lessonIds);

    Optional<ChildProgress> findByIdChildIdAndIdLessonId(
            UUID childId,
            UUID lessonId
    );

    // Số bài đã xong của từng (khóa, bé): [course_id, child_id, số bài]. Dùng cho trang khóa học đã mua của phụ huynh.
    @Query(value = """
            SELECT l.course_id, p.child_id, COUNT(*) FROM learning_progress p
            JOIN lessons l ON l.id = p.lesson_id
            WHERE p.is_completed AND l.course_id IN (:courseIds) AND p.child_id IN (:childIds)
            GROUP BY l.course_id, p.child_id
            """, nativeQuery = true)
    List<Object[]> countCompletedByCourseAndChild(@Param("courseIds") Collection<UUID> courseIds,
                                                  @Param("childIds") Collection<UUID> childIds);

    // Atomic upsert: two first-time requests for the same lesson cannot collide on the primary key,
    // watched time never goes down and a completed lesson stays completed.
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO learning_progress (child_id, lesson_id, watched_seconds, is_completed)
            VALUES (:childId, :lessonId, :seconds, :completed)
            ON CONFLICT (child_id, lesson_id) DO UPDATE SET
                watched_seconds = GREATEST(learning_progress.watched_seconds, EXCLUDED.watched_seconds),
                is_completed = learning_progress.is_completed OR EXCLUDED.is_completed
            """, nativeQuery = true)
    void upsert(@Param("childId") UUID childId,
                @Param("lessonId") UUID lessonId,
                @Param("seconds") int seconds,
                @Param("completed") boolean completed);
}
