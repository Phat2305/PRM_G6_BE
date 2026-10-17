package com.example.art_course.modules.course.repository;

import com.example.art_course.modules.course.entity.CourseReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("be2CourseReviewRepository")
public interface CourseReviewRepository extends JpaRepository<CourseReview, UUID> {
    Optional<CourseReview> findByCourseIdAndParentId(UUID courseId, UUID parentId);
    java.util.List<CourseReview> findByCourseIdOrderByCreatedAtDesc(UUID courseId);
}
