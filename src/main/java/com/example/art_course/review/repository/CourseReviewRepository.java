
        package com.example.art_course.review.repository;

import com.example.art_course.review.entity.CourseReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseReviewRepository
        extends JpaRepository<CourseReview, UUID> {

    List<CourseReview> findAllByCourseId(UUID courseId);

    List<CourseReview> findAllByChildId(UUID childId);

    List<CourseReview> findAllByParentId(UUID parentId);

    Optional<CourseReview> findByCourseIdAndChildId(
            UUID courseId,
            UUID childId
    );
}
