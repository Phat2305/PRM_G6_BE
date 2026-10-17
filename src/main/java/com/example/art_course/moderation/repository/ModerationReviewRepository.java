
        package com.example.art_course.moderation.repository;

import com.example.art_course.moderation.entity.ModerationReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModerationReviewRepository
        extends JpaRepository<ModerationReview, UUID> {

    List<ModerationReview> findAllByStaffId(UUID staffId);

    List<ModerationReview> findAllByTargetType(String targetType);

    List<ModerationReview> findAllByTargetId(UUID targetId);

    List<ModerationReview> findAllByTargetTypeAndTargetId(
            String targetType,
            UUID targetId
    );

    List<ModerationReview> findAllByDecision(String decision);
}
