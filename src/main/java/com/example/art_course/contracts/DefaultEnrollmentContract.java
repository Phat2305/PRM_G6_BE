package com.example.art_course.contracts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class DefaultEnrollmentContract implements IEnrollmentContract {

    @Override
    public void grantAccess(UUID childId, UUID courseId, UUID orderId) {
        log.info("[Standalone Enrollment Contract] Cấp quyền học mặc định cho childId: {}, courseId: {}, orderId: {}", childId, courseId, orderId);
    }

    @Override
    public void revokeAccess(UUID childId, UUID courseId, UUID refundId) {
        log.info("[Standalone Enrollment Contract] Thu hồi quyền học mặc định cho childId: {}, courseId: {}, refundId: {}", childId, courseId, refundId);
    }

    @Override
    public boolean isEnrolled(UUID childId, UUID courseId) {
        log.debug("[Standalone Enrollment Contract] Kiểm tra quyền học: childId={}, courseId={}", childId, courseId);
        return false;
    }
}
