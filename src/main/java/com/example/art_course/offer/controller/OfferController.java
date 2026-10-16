
package com.example.art_course.offer.controller;

import com.example.art_course.offer.dto.CreateOfferRequest;
import com.example.art_course.offer.dto.OfferResponse;
import com.example.art_course.offer.dto.UpdateOfferStatusRequest;
import com.example.art_course.offer.service.OfferService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping
    public ResponseEntity<OfferResponse> create(
            @Valid @RequestBody CreateOfferRequest request,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(offerService.create(
                        request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfferResponse> getById(
            @PathVariable UUID id,
            Authentication authentication) {

        return ResponseEntity.ok(
                offerService.getById(id, authentication.getName()));
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<List<OfferResponse>> getByRequestId(
            @PathVariable UUID requestId,
            Authentication authentication) {

        return ResponseEntity.ok(
                offerService.getByRequestId(
                        requestId, authentication.getName()));
    }

    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<List<OfferResponse>> getByTeacherId(
            @PathVariable UUID teacherId,
            Authentication authentication) {

        return ResponseEntity.ok(
                offerService.getByTeacherId(
                        teacherId, authentication.getName()));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<OfferResponse>> getByStatus(
            @PathVariable String status,
            Authentication authentication) {

        return ResponseEntity.ok(
                offerService.getByStatus(
                        status, authentication.getName()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OfferResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOfferStatusRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                offerService.updateStatus(
                        id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            Authentication authentication) {

        offerService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
