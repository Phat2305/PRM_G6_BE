
        package com.example.art_course.enrollment.repository;

import com.example.art_course.enrollment.entity.Enrollment;
import com.example.art_course.enrollment.entity.EnrollmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository
        extends JpaRepository<Enrollment, EnrollmentId> {

    List<Enrollment> findAllByIdChildId(UUID childId);

    List<Enrollment> findAllByIdCourseId(UUID courseId);

    Optional<Enrollment> findByIdChildIdAndIdCourseId(
            UUID childId,
            UUID courseId
    );

    boolean existsByIdChildIdAndIdCourseId(
            UUID childId,
            UUID courseId
    );

    boolean existsByIdChildIdAndIdCourseIdAndStatus(
            UUID childId,
            UUID courseId,
            String status
    );
}
