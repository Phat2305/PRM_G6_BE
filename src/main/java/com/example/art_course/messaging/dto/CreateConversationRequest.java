package com.example.art_course.messaging.dto;

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

    @NotNull
    private UUID parentId;

    @NotNull
    private UUID teacherId;

    private UUID lessonId;

    @NotBlank
    private String contextType;

    @NotNull
    private UUID contextId;
}