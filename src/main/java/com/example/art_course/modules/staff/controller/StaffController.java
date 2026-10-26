package com.example.art_course.modules.staff.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.staff.dto.ComplaintDto;
import com.example.art_course.modules.staff.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    /**
     * Staff kiểm duyệt / ẩn / gỡ dụng cụ vi phạm (STF-02/STF-07).
     */
    @PostMapping("/supplies/{supplyId}/moderate")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<Supply> moderateSupply(
            @PathVariable UUID supplyId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        UUID staffId = UUID.fromString(auth.getName());
        String action = body.get("action");
        String reason = body.get("reason");
        Supply supply = staffService.moderateSupply(supplyId, staffId, action, reason);
        return ApiResponse.ok("Xử lý kiểm duyệt dụng cụ thành công", supply);
    }

    /**
     * Việc khiếu nại/sự cố cần xử lý: hoàn tiền chờ duyệt và dòng dụng cụ giao thất bại, kèm tên hai bên (STF-04).
     */
    @GetMapping("/complaints")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<List<ComplaintDto>> getComplaints() {
        return ApiResponse.ok(staffService.getComplaints());
    }

    /**
     * Xem trao đổi tin nhắn giải quyết khiếu nại có kiểm soát (STF-04/MSG-06).
     */
    @GetMapping("/complaints/{orderId}/conversations")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<Map<String, Object>> getComplaintConversation(
            @PathVariable UUID orderId,
            Authentication auth
    ) {
        UUID staffId = UUID.fromString(auth.getName());
        return ApiResponse.ok(staffService.getComplaintConversation(orderId, staffId));
    }
}
