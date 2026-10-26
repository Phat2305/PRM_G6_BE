
package com.example.art_course.course.repository;

import com.example.art_course.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findAllByTeacherId(UUID teacherId);
    // Khóa học riêng đã làm cho một yêu cầu customize (CUS-11)
    java.util.Optional<Course> findFirstByCustomizeRequestId(UUID customizeRequestId);

    List<Course> findAllByStatus(String status);

    List<Course> findAllByTeacherIdAndStatus(
            UUID teacherId,
            String status
    );
}
