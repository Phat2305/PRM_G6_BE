
        package com.example.art_course.refund.controller;

import com.example.art_course.refund.dto.CreateRefundRequest;
import com.example.art_course.refund.dto.RefundResponse;
import com.example.art_course.refund.dto.UpdateRefundRequest;
import com.example.art_course.refund.service.RefundService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<RefundResponse> create(
            @Valid @RequestBody CreateRefundRequest request) {

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

    @PatchMapping("/{id}")
    public ResponseEntity<RefundResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRefundRequest request) {

        return ResponseEntity.ok(
                refundService.update(id, request));
    }
}
