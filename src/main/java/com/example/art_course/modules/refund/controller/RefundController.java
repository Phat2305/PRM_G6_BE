package com.example.art_course.modules.refund.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.modules.refund.dto.CompleteRefundRequest;
import com.example.art_course.modules.refund.dto.CreateRefundRequest;
import com.example.art_course.modules.refund.dto.RefundResponseDto;
import com.example.art_course.modules.refund.dto.RejectRefundRequest;
import com.example.art_course.modules.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController("be2RefundController")
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final TeacherNames names;

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
     * Parent xem các yêu cầu hoàn tiền của mình (FIN-07).
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<List<RefundResponseDto>> myRefunds(Authentication auth) {
        return ApiResponse.ok(refundService.listMyRefunds(UUID.fromString(auth.getName())));
    }

    /**
     * Staff/Admin xem danh sách hoàn tiền, mới nhất trước, lọc theo status, phân trang (page từ 0, size 1-200) (FIN-08).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<List<RefundResponseDto>> listRefunds(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        List<RefundResponseDto> list = refundService.listRefunds(status, Math.max(0, page), Math.min(Math.max(1, size), 200));
        Map<UUID, String> parents = names.parentsByIds(list.stream().map(RefundResponseDto::getParentId).distinct().toList());
        list.forEach(dto -> dto.setParentName(parents.get(dto.getParentId())));
        return ApiResponse.ok(list);
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
     * Staff/Admin từ chối yêu cầu hoàn tiền đang chờ duyệt, kèm lý do (FIN-08).
     */
    @PostMapping("/{refundId}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<RefundResponseDto> rejectRefund(
            @PathVariable UUID refundId,
            @Valid @RequestBody RejectRefundRequest req,
            Authentication auth
    ) {
        UUID staffId = UUID.fromString(auth.getName());
        RefundResponseDto dto = refundService.rejectRefund(refundId, staffId, req.getReason());
        return ApiResponse.ok("Từ chối yêu cầu hoàn tiền thành công", dto);
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
