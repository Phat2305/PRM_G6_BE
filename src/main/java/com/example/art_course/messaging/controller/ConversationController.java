package com.example.art_course.messaging.controller;

import com.example.art_course.messaging.dto.ConversationResponse;
import com.example.art_course.messaging.dto.CreateConversationRequest;
import com.example.art_course.messaging.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @Valid @RequestBody CreateConversationRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getById(id, authentication.getName()));
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<ConversationResponse>> getByParentId(
            @PathVariable UUID parentId,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getByParentId(parentId, authentication.getName()));
    }

    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<List<ConversationResponse>> getByTeacherId(
            @PathVariable UUID teacherId,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.getByTeacherId(teacherId, authentication.getName()));
    }

    @GetMapping("/context")
    public ResponseEntity<List<ConversationResponse>> getByContext(
            @RequestParam String contextType,
            @RequestParam UUID contextId,
            Authentication authentication) {
        return ResponseEntity.ok(service.getByContext(
                contextType, contextId, authentication.getName()));
    }
}