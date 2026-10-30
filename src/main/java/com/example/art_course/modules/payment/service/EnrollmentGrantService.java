package com.example.art_course.modules.payment.service;

import com.example.art_course.contracts.IEnrollmentContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service trung gian bọc lệnh gọi sang BE1 với cơ chế retry tự động (@Retryable).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentGrantService {

    private final IEnrollmentContract enrollmentContract;

    @Retryable(
            retryFor = { RuntimeException.class, Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void grantAccessWithRetry(UUID childId, UUID courseId, UUID orderId) {
        log.info("[RETRYABLE-BE1] Đang gọi grantAccess cho Child {}, Course {}, Order {}", childId, courseId, orderId);
        enrollmentContract.grantAccess(childId, courseId, orderId);
    }

    @Recover
    public void recoverGrantAccess(Exception ex, UUID childId, UUID courseId, UUID orderId) {
        log.error("[RETRYABLE-BE1-FAIL] Cấp quyền học thất bại sau các lần thử lại: Child {}, Course {}, Order {}. Lỗi: {}",
                childId, courseId, orderId, ex.getMessage());
        throw ex instanceof RuntimeException runtime ? runtime : new IllegalStateException(ex);
    }
}
