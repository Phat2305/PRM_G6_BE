package com.example.art_course.messaging.repository;

import com.example.art_course.messaging.dto.ConversationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Hộp thư của một người trong một câu SQL: tên hai bên, tiêu đề ngữ cảnh, tin cuối, số tin chưa đọc.
@Repository
@RequiredArgsConstructor
public class ConversationSummaryRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String SQL = """
            SELECT c.id, c.parent_id, c.teacher_id, c.lesson_id, c.context_type, c.context_id,
                   pp.full_name AS parent_name,
                   tp.full_name AS teacher_name,
                   COALESCE(cr.title, l.title) AS context_title,
                   lm.content AS last_message,
                   lm.created_at AS last_message_at,
                   (SELECT COUNT(*) FROM messages m
                     WHERE m.conversation_id = c.id
                       AND m.sender_id <> ?
                       AND m.created_at > COALESCE(
                           CASE WHEN c.parent_id = ? THEN c.parent_last_read_at
                                ELSE c.teacher_last_read_at END,
                           '-infinity'::timestamptz)) AS unread_count
              FROM conversations c
              LEFT JOIN parent_profiles pp ON pp.user_id = c.parent_id
              LEFT JOIN teacher_profiles tp ON tp.user_id = c.teacher_id
              LEFT JOIN customize_requests cr ON cr.id = c.context_id
              LEFT JOIN lessons l ON l.id = COALESCE(c.lesson_id, c.context_id)
              LEFT JOIN LATERAL (
                    SELECT m.content, m.created_at FROM messages m
                     WHERE m.conversation_id = c.id
                     ORDER BY m.created_at DESC LIMIT 1) lm ON TRUE
             WHERE c.parent_id = ? OR c.teacher_id = ?
             ORDER BY lm.created_at DESC NULLS LAST
            """;

    public List<ConversationSummary> forUser(UUID userId) {
        return jdbcTemplate.query(SQL, (rs, i) -> new ConversationSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                rs.getObject("teacher_id", UUID.class),
                rs.getObject("lesson_id", UUID.class),
                rs.getString("context_type"),
                rs.getObject("context_id", UUID.class),
                rs.getString("parent_name"),
                rs.getString("teacher_name"),
                rs.getString("context_title"),
                rs.getString("last_message"),
                rs.getObject("last_message_at", OffsetDateTime.class),
                rs.getInt("unread_count")), userId, userId, userId, userId);
    }
}
