package com.example.art_course.messaging.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateConversationRequest {

    @JsonAlias({"parentId", "parent_id"})
    private UUID parentId;

    @JsonAlias({"teacherId", "teacher_id"})
    private UUID teacherId;

    @JsonAlias({"lessonId", "lesson_id"})
    private UUID lessonId;

    @NotBlank
    @JsonAlias({"contextType", "context_type", "type"})
    private String contextType;

    @NotNull
    @JsonAlias({"contextId", "context_id", "targetId", "target_id"})
    private UUID contextId;
}