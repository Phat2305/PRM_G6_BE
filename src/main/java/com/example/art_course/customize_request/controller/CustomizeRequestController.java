
package com.example.art_course.customize_request.controller;

import com.example.art_course.customize_request.dto.CreateCustomizeRequestRequest;
import com.example.art_course.customize_request.dto.CustomizeRequestResponse;
import com.example.art_course.customize_request.dto.UpdateCustomizeRequestRequest;
import com.example.art_course.customize_request.service.CustomizeRequestService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customize-requests")
public class CustomizeRequestController {

    private final CustomizeRequestService service;

    public CustomizeRequestController(CustomizeRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CustomizeRequestResponse> create(
            @Valid @RequestBody CreateCustomizeRequestRequest request,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomizeRequestResponse> getById(
            @PathVariable UUID id,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.getById(id, authentication.getName()));
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<CustomizeRequestResponse>> getByParentId(
            @PathVariable UUID parentId,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.getByParentId(parentId, authentication.getName()));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<CustomizeRequestResponse>> getByStatus(
            @PathVariable String status,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.getByStatus(status, authentication.getName()));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<CustomizeRequestResponse>> getByCategory(
            @PathVariable String category,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.getByCategory(category, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomizeRequestResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomizeRequestRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.update(id, request, authentication.getName()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CustomizeRequestResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.updateStatus(id, status, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            Authentication authentication) {

        service.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
