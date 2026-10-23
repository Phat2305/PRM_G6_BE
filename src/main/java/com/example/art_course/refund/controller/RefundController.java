
        package com.example.art_course.refund.controller;

import com.example.art_course.refund.dto.CreateRefundRequest;
import com.example.art_course.refund.dto.RefundResponse;
import com.example.art_course.refund.dto.UpdateRefundRequest;
import com.example.art_course.refund.service.RefundService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;
    private final com.example.art_course.user.repository.UserRepository userRepository;

    public RefundController(
            RefundService refundService,
            com.example.art_course.user.repository.UserRepository userRepository) {
        this.refundService = refundService;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasRole('PARENT')")
    @PostMapping
    public ResponseEntity<RefundResponse> create(
            @Valid @RequestBody CreateRefundRequest request,
            org.springframework.security.core.Authentication authentication) {

        if (request.getParentId() == null && authentication != null && authentication.isAuthenticated()) {
            String name = authentication.getName();
            try {
                UUID uid = UUID.fromString(name);
                if (userRepository.existsById(uid)) {
                    request.setParentId(uid);
                }
            } catch (IllegalArgumentException ignored) {
            }
            if (request.getParentId() == null) {
                userRepository.findByUsername(name).ifPresent(u -> request.setParentId(u.getId()));
            }
        }

        if (request.getParentId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "parentId is required"
            );
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(refundService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RefundResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(refundService.getById(id));
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<RefundResponse>> getByParentId(
            @PathVariable UUID parentId) {

        return ResponseEntity.ok(
                refundService.getByParentId(parentId));
    }

    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<List<RefundResponse>> getByPaymentId(
            @PathVariable UUID paymentId) {

        return ResponseEntity.ok(
                refundService.getByPaymentId(paymentId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<RefundResponse>> getByStatus(
            @PathVariable String status) {

        return ResponseEntity.ok(refundService.getByStatus(status));
    }

    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<RefundResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRefundRequest request) {

        return ResponseEntity.ok(
                refundService.update(id, request));
    }
}
