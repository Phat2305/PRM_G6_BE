package com.example.art_course.modules.course.repository;

import com.example.art_course.modules.course.entity.CourseReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository("be2CourseReviewRepository")
public interface CourseReviewRepository extends JpaRepository<CourseReview, UUID> {
    Optional<CourseReview> findByCourseIdAndParentId(UUID courseId, UUID parentId);
    java.util.List<CourseReview> findByCourseIdOrderByCreatedAtDesc(UUID courseId);

    // Điểm trung bình và số đánh giá của nhiều khóa trong một truy vấn (khóa chưa có đánh giá thì không có dòng).
    @Query("select r.courseId as courseId, avg(r.rating) as average, count(r) as total "
            + "from Be2CourseReview r where r.courseId in :ids group by r.courseId")
    java.util.List<RatingSummary> summarize(@Param("ids") Collection<UUID> ids);

    interface RatingSummary {
        UUID getCourseId();

        Double getAverage();

        Long getTotal();
    }
}
