package com.example.art_course.modules.staff.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// STF-01: bài đăng yêu cầu customize chờ Staff duyệt.
public record RequestReviewItemDto(UUID id, UUID parentId, String parentName, String title, String description,
                                   String category, Short childAge, BigDecimal budget, OffsetDateTime deadline,
                                   int rejectionCount) {
}
