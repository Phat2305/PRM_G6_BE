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
public class CreateMessageRequest {

    @NotNull
    @JsonAlias({"conversationId", "conversation_id"})
    private UUID conversationId;

    @JsonAlias({"senderId", "sender_id"})
    private UUID senderId;

    @NotBlank
    @JsonAlias({"content", "message", "text"})
    private String content;

    @JsonAlias({"isOffer", "is_offer"})
    private Boolean isOffer;
}