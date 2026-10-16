package com.example.art_course.messaging.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateMessageRequest {

    @NotNull
    private UUID conversationId;

    @NotNull
    private UUID senderId;

    @NotBlank
    private String content;

    private Boolean isOffer;
}