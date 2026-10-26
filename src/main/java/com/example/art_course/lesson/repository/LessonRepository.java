
package com.example.art_course.lesson.repository;

import com.example.art_course.lesson.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findAllByCourseId(UUID courseId);

    List<Lesson> findAllByCourseIdAndStatus(UUID courseId, String status);

    // Tìm bài học dùng một video HLS (kiểm quyền xem video).
    List<Lesson> findAllByVideoUrlContaining(String fragment);
}
