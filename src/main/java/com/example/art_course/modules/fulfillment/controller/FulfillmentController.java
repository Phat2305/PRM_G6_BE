package com.example.art_course.modules.fulfillment.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.fulfillment.dto.DeliveryStatusResponseDto;
import com.example.art_course.modules.fulfillment.dto.ShippingUpdateRequest;
import com.example.art_course.modules.fulfillment.dto.TeacherConfirmDeliveryRequest;
import com.example.art_course.modules.fulfillment.service.FulfillmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    /**
     * Giáo viên xác nhận hoặc từ chối dòng dụng cụ (FUL-01).
     */
    @PostMapping("/teacher/items/{orderDetailId}/confirm-or-reject")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<DeliveryStatusResponseDto> teacherConfirmOrReject(
            @PathVariable UUID orderDetailId,
            @Valid @RequestBody TeacherConfirmDeliveryRequest request,
            Authentication auth
    ) {
        UUID teacherId = UUID.fromString(auth.getName());
        DeliveryStatusResponseDto dto = fulfillmentService.teacherConfirmOrReject(orderDetailId, teacherId, request);
        return ApiResponse.success(dto);
    }

    /**
     * Giáo viên cập nhật thông tin vận chuyển và chuyển sang SHIPPING (FUL-02).
     */
    @PostMapping("/teacher/items/{orderDetailId}/shipping")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<DeliveryStatusResponseDto> teacherUpdateShipping(
            @PathVariable UUID orderDetailId,
            @Valid @RequestBody ShippingUpdateRequest request,
            Authentication auth
    ) {
        UUID teacherId = UUID.fromString(auth.getName());
        DeliveryStatusResponseDto dto = fulfillmentService.teacherUpdateShipping(orderDetailId, teacherId, request);
        return ApiResponse.success(dto);
    }

    /**
     * Báo đã giao tới nơi (DELIVERED).
     */
    @PostMapping("/delivered/{orderDetailId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF', 'ADMIN')")
    public ApiResponse<DeliveryStatusResponseDto> markDelivered(
            @PathVariable UUID orderDetailId,
            Authentication auth
    ) {
        UUID actorId = UUID.fromString(auth.getName());
        DeliveryStatusResponseDto dto = fulfillmentService.markDelivered(orderDetailId, actorId);
        return ApiResponse.success(dto);
    }

    /**
     * Phụ huynh xác nhận đã nhận hàng (FUL-03: COMPLETED).
     */
    @PostMapping("/parent/items/{orderDetailId}/confirm-received")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<DeliveryStatusResponseDto> parentConfirmReceived(
            @PathVariable UUID orderDetailId,
            Authentication auth
    ) {
        UUID parentId = UUID.fromString(auth.getName());
        DeliveryStatusResponseDto dto = fulfillmentService.parentConfirmReceived(orderDetailId, parentId);
        return ApiResponse.success(dto);
    }

    /**
     * Phụ huynh hủy dòng dụng cụ khi giáo viên chưa xác nhận (FUL-06: CANCELLED).
     */
    @PostMapping("/parent/items/{orderDetailId}/cancel")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<DeliveryStatusResponseDto> parentCancelItem(
            @PathVariable UUID orderDetailId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth
    ) {
        UUID parentId = UUID.fromString(auth.getName());
        String reason = body != null ? body.get("reason") : null;
        DeliveryStatusResponseDto dto = fulfillmentService.parentCancelItem(orderDetailId, parentId, reason);
        return ApiResponse.success(dto);
    }

    /**
     * Staff xử lý giao thất bại / trả hàng (FUL-07, FUL-08: RE_SHIP hoặc REFUND).
     */
    @PostMapping("/staff/items/{orderDetailId}/handle-failure")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<DeliveryStatusResponseDto> staffHandleFailure(
            @PathVariable UUID orderDetailId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        UUID staffId = UUID.fromString(auth.getName());
        String action = body.get("action");
        String reason = body.get("reason");
        DeliveryStatusResponseDto dto = fulfillmentService.staffHandleDeliveryFailed(orderDetailId, staffId, action, reason);
        return ApiResponse.success(dto);
    }
}
