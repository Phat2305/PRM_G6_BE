package com.example.art_course.messaging.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// MSG-05: một dòng hộp thư, đủ để FE hiện danh sách mà không phải gọi thêm.
public record ConversationSummary(
        UUID id,
        UUID parentId,
        UUID teacherId,
        UUID lessonId,
        String contextType,
        UUID contextId,
        String parentName,
        String teacherName,
        String contextTitle,
        String lastMessage,
        OffsetDateTime lastMessageAt,
        int unreadCount) {
}
