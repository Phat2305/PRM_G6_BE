package com.example.art_course.modules.refund.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.refund.dto.CompleteRefundRequest;
import com.example.art_course.modules.refund.dto.CreateRefundRequest;
import com.example.art_course.modules.refund.dto.RefundResponseDto;
import com.example.art_course.modules.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController("be2RefundController")
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /**
     * Parent gửi yêu cầu hoàn tiền (FIN-07).
     */
    @PostMapping("/request")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<RefundResponseDto> requestRefund(
            @Valid @RequestBody CreateRefundRequest request,
            Authentication auth
    ) {
        UUID parentId = UUID.fromString(auth.getName());
        RefundResponseDto dto = refundService.requestRefund(parentId, request);
        return ApiResponse.ok("Gửi yêu cầu hoàn tiền thành công", dto);
    }

    /**
     * Staff/Admin duyệt yêu cầu hoàn tiền (FIN-08).
     */
    @PostMapping("/{refundId}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<RefundResponseDto> approveRefund(
            @PathVariable UUID refundId,
            Authentication auth
    ) {
        UUID staffId = UUID.fromString(auth.getName());
        RefundResponseDto dto = refundService.approveRefund(refundId, staffId);
        return ApiResponse.ok("Duyệt yêu cầu hoàn tiền thành công", dto);
    }

    /**
     * Staff hoàn tất chi tiền hoàn kèm mã giao dịch (FIN-08, FIN-09).
     */
    @PostMapping("/{refundId}/complete")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<RefundResponseDto> completeRefund(
            @PathVariable UUID refundId,
            @Valid @RequestBody CompleteRefundRequest req,
            Authentication auth
    ) {
        UUID staffId = UUID.fromString(auth.getName());
        RefundResponseDto dto = refundService.completeRefund(refundId, staffId, req);
        return ApiResponse.ok("Xác nhận hoàn tiền thành công", dto);
    }
}
