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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

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
    @PostMapping(value = "/{refundId}/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
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

    @PostMapping(value = "/{refundId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<RefundResponseDto> completeWithProof(@PathVariable UUID refundId,
            @RequestParam(value = "file", required = false) MultipartFile file, Authentication auth) {
        return ApiResponse.ok("Xác nhận hoàn tiền thành công",
                refundService.completeWithProof(refundId, UUID.fromString(auth.getName()), file));
    }

    @GetMapping("/{refundId}/proof")
    @PreAuthorize("hasAnyRole('PARENT', 'STAFF', 'ADMIN')")
    public ResponseEntity<Resource> proof(@PathVariable UUID refundId, Authentication auth) {
        boolean reviewer = auth.getAuthorities().stream()
                .anyMatch(a -> Set.of("ROLE_STAFF", "ROLE_ADMIN").contains(a.getAuthority()));
        var object = refundService.proof(refundId, UUID.fromString(auth.getName()), reviewer);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(object.response().contentType()))
                .header("X-Content-Type-Options", "nosniff").header("Cache-Control", "private, no-store")
                .body(new InputStreamResource(object));
    }
}
