package com.example.art_course.messaging.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "context_type", nullable = false, length = 50)
    private String contextType;

    @Column(name = "context_id", nullable = false)
    private UUID contextId;

    // Mốc đã đọc của từng bên; tin của bên kia tạo sau mốc này là chưa đọc (docs/sql/2026-10-25_chat_offer.sql).
    @Column(name = "parent_last_read_at")
    private OffsetDateTime parentLastReadAt;

    @Column(name = "teacher_last_read_at")
    private OffsetDateTime teacherLastReadAt;
}