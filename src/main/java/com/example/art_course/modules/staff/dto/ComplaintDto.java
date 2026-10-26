package com.example.art_course.modules.staff.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// STF-04: một việc Staff cần xử lý. kind = REFUND_REQUEST (yêu cầu hoàn tiền chờ duyệt, xử lý ở màn hoàn tiền)
// hoặc DELIVERY_FAILED (dòng dụng cụ giao thất bại, Staff chọn RE_SHIP hoặc REFUND).
public record ComplaintDto(
        String kind,
        UUID orderId,
        UUID orderDetailId,
        UUID refundId,
        UUID parentId,
        String parentName,
        UUID teacherId,
        String teacherName,
        String subject,
        String reason,
        BigDecimal amount,
        Instant createdAt
) {
}
