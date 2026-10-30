package com.example.art_course.modules.moderation.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.moderation.dto.ModerationReviewRequest;
import com.example.art_course.modules.moderation.entity.ModerationReview;
import com.example.art_course.modules.moderation.service.ModerationService;
import com.example.art_course.modules.admin.dto.StaffActivityDto;
import com.example.art_course.modules.admin.service.AdminAuditDetailsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;
    private final AdminAuditDetailsService auditDetails;

    @PostMapping("/reviews")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<Void> recordReview(
            @Valid @RequestBody ModerationReviewRequest req,
            Authentication auth
    ) {
        UUID staffId = UUID.fromString(auth.getName());
        moderationService.recordModerationReview(staffId, req.getTargetType(), req.getTargetId(), req.getDecision(), req.getReason());
        return ApiResponse.ok("Ghi nhận nhật ký kiểm duyệt thành công", null);
    }

    @GetMapping("/reviews/{targetType}/{targetId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<List<ModerationReview>> getReviewsByTarget(
            @PathVariable String targetType,
            @PathVariable UUID targetId
    ) {
        return ApiResponse.ok(moderationService.getReviewsByTarget(targetType, targetId));
    }

    @GetMapping("/reviews/staff/{staffId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<StaffActivityDto>> getReviewsByStaff(@PathVariable UUID staffId) {
        return ApiResponse.ok(auditDetails.staffActivities(staffId, moderationService.getReviewsByStaff(staffId)));
    }
}
