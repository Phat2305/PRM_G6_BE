package com.example.art_course.modules.moderation.repository;

import com.example.art_course.modules.moderation.entity.ModerationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository("be2ModerationReviewRepository")
public interface ModerationReviewRepository extends JpaRepository<ModerationReview, UUID> {
    List<ModerationReview> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, UUID targetId);
    List<ModerationReview> findByStaffIdOrderByCreatedAtDesc(UUID staffId);
}
