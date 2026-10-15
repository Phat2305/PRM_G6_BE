package com.example.art_course.contracts;

import java.util.UUID;

/**
 * Hợp đồng ghi nhận quyết định kiểm duyệt (STF-07) giữa BE1 và BE2.
 */
public interface IModerationContract {
    void recordModerationReview(UUID staffId, String targetType, UUID targetId, String decision, String reason);
}
