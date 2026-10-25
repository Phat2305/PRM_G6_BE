package com.example.art_course.modules.staff.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.staff.service.StaffService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final UserRepository userRepository;
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
     * Danh sách đơn hàng có khiếu nại hoặc tranh chấp (STF-04).
     */
    @GetMapping("/complaints")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<List<Order>> getComplaintOrders() {
        return ApiResponse.ok(staffService.getComplaintOrders());
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

    @GetMapping("/teachers/pending")
    @PreAuthorize("hasRole('STAFF')")
    public List<Map<String, Object>> getPendingTeachers() {
        return userRepository.findAll().stream()
                .filter(user ->
                        "TEACHER".equals(user.getRole())
                                && "PENDING".equals(user.getStatus()))
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "role", user.getRole(),
                        "status", user.getStatus()
                ))
                .toList();
    }

    @PatchMapping("/teachers/{userId}/approve")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> approveTeacher(
            @PathVariable UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        if (!"TEACHER".equals(user.getRole())
                || !"PENDING".equals(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is not a pending teacher"
            );
        }

        user.setStatus("ACTIVE");
        userRepository.save(user);

        return Map.<String, Object>of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole(),
                "status", user.getStatus()
        );
    }

}
