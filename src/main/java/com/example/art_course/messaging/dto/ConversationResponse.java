package com.example.art_course.messaging.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationResponse {

    private UUID id;
    private UUID parentId;
    private UUID teacherId;
    private UUID lessonId;
    private String contextType;
    private UUID contextId;
}