package com.example.art_course.messaging.entity;

import jakarta.persistence.*;
import lombok.*;

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
}