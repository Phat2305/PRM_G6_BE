package com.example.art_course.modules.admin.dto;

import java.time.Instant;
import java.util.UUID;

// ADM-05: quyết định gốc và thông tin đối tượng được xem riêng biệt.
public record StaffActivityDto(UUID id, UUID staffId, String targetType, UUID targetId, String decision,
                               String reason, Instant createdAt, AuditDetailsDto details) {}
