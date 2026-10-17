
package com.example.art_course.course.repository;

import com.example.art_course.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findAllByTeacherId(UUID teacherId);

    List<Course> findAllByStatus(String status);

    List<Course> findAllByTeacherIdAndStatus(
            UUID teacherId,
            String status
    );
}
