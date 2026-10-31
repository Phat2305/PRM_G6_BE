
package com.example.art_course.lesson.repository;

import com.example.art_course.lesson.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findAllByCourseId(UUID courseId);

    List<Lesson> findAllByCourseIdAndStatus(UUID courseId, String status);

    // Tổng số bài của từng khóa: [course_id, số bài].
    @org.springframework.data.jpa.repository.Query(value =
            "SELECT course_id, COUNT(*) FROM lessons WHERE course_id IN (:courseIds) GROUP BY course_id",
            nativeQuery = true)
    List<Object[]> countByCourseIds(
            @org.springframework.data.repository.query.Param("courseIds") java.util.Collection<UUID> courseIds);

    // Tìm bài học dùng một video HLS (kiểm quyền xem video).
    List<Lesson> findAllByVideoUrlContaining(String fragment);
}
