package com.example.art_course.modules.moderation.service;

import com.example.art_course.common.AppError;
import com.example.art_course.contracts.IModerationContract;
import com.example.art_course.modules.moderation.entity.ModerationReview;
import com.example.art_course.modules.moderation.repository.ModerationReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService implements IModerationContract {

    private final ModerationReviewRepository moderationReviewRepository;

    private static final Set<String> VALID_TARGET_TYPES = Set.of(
            "TEACHER_PROFILE", "COURSE", "SUPPLY", "CUSTOMIZE_REQUEST", "REFUND"
    );

    private static final Set<String> VALID_DECISIONS = Set.of(
            "APPROVED", "REJECTED", "FLAGGED", "RESOLVED"
    );

    @Override
    @Transactional
    public void recordModerationReview(UUID staffId, String targetType, UUID targetId, String decision, String reason) {
        String cleanType = targetType != null ? targetType.trim().toUpperCase() : "";
        String cleanDecision = decision != null ? decision.trim().toUpperCase() : "";

        if (!VALID_TARGET_TYPES.contains(cleanType)) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_TARGET_TYPE",
                    "Loại đối tượng kiểm duyệt không hợp lệ: " + targetType);
        }

        if (!VALID_DECISIONS.contains(cleanDecision)) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_DECISION",
                    "Quyết định kiểm duyệt không hợp lệ: " + decision);
        }

        ModerationReview review = ModerationReview.builder()
                .staffId(staffId)
                .targetType(cleanType)
                .targetId(targetId)
                .decision(cleanDecision)
                .reason(reason)
                .build();

        moderationReviewRepository.save(review);
        log.info("[MODERATION] Staff {} ghi nhận quyết định {} cho {} (ID: {})",
                staffId, cleanDecision, cleanType, targetId);
    }

    @Transactional(readOnly = true)
    public List<ModerationReview> getReviewsByTarget(String targetType, UUID targetId) {
        return moderationReviewRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                targetType.toUpperCase(), targetId
        );
    }

    @Transactional(readOnly = true)
    public List<ModerationReview> getReviewsByStaff(UUID staffId) {
        return moderationReviewRepository.findByStaffIdOrderByCreatedAtDesc(staffId);
    }
}
