
        package com.example.art_course.refund.service;

import com.example.art_course.refund.dto.CreateRefundRequest;
import com.example.art_course.refund.dto.RefundResponse;
import com.example.art_course.refund.dto.UpdateRefundRequest;
import com.example.art_course.refund.entity.Refund;
import com.example.art_course.refund.repository.RefundRepository;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RefundService {

    private final RefundRepository refundRepository;

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("pending", "approved", "rejected", "completed");

    public RefundService(RefundRepository refundRepository) {
        this.refundRepository = refundRepository;
    }

    @Transactional
    public RefundResponse create(CreateRefundRequest request) {
        Refund refund = Refund.builder()
                .parentId(request.getParentId())
                .paymentId(request.getPaymentId())
                .totalAmount(request.getTotalAmount())
                .bankInfo(request.getBankInfo())
                .status("pending")
                .build();

        return toResponse(refundRepository.save(refund));
    }

    public RefundResponse getById(UUID id) {
        return toResponse(findById(id));
    }

    public List<RefundResponse> getByParentId(UUID parentId) {
        return refundRepository.findAllByParentId(parentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<RefundResponse> getByPaymentId(UUID paymentId) {
        return refundRepository.findAllByPaymentId(paymentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<RefundResponse> getByStatus(String status) {
        String normalizedStatus = status.trim().toLowerCase();

        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid refund status");
        }

        return refundRepository.findAllByStatus(normalizedStatus)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RefundResponse update(
            UUID id,
            UpdateRefundRequest request) {

        Refund refund = findById(id);
        String status = request.getStatus().trim().toLowerCase();

        if (!ALLOWED_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid refund status");
        }

        refund.setStatus(status);
        refund.setBankInfo(request.getBankInfo());

        return toResponse(refundRepository.save(refund));
    }

    private Refund findById(UUID id) {
        return refundRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Refund not found"));
    }

    private RefundResponse toResponse(Refund refund) {
        return RefundResponse.builder()
                .id(refund.getId())
                .parentId(refund.getParentId())
                .totalAmount(refund.getTotalAmount())
                .status(refund.getStatus())
                .expiredAt(refund.getExpiredAt())
                .bankInfo(refund.getBankInfo())
                .updatedAt(refund.getUpdatedAt())
                .paymentId(refund.getPaymentId())
                .build();
    }
}
