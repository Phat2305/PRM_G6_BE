package com.example.art_course.messaging.controller;

import com.example.art_course.messaging.dto.CreateMessageRequest;
import com.example.art_course.messaging.dto.MessageResponse;
import com.example.art_course.messaging.service.MessageService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> create(
            @Valid @RequestBody CreateMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MessageResponse> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getById(id, authentication.getName()));
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<MessageResponse>> getByConversationId(
            @PathVariable UUID conversationId,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getByConversationId(
                        conversationId, authentication.getName()));
    }

    @GetMapping("/sender/{senderId}")
    public ResponseEntity<List<MessageResponse>> getBySenderId(
            @PathVariable UUID senderId,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getBySenderId(senderId, authentication.getName()));
    }
}