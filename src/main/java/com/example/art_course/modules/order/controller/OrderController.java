package com.example.art_course.modules.order.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.service.OrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final com.example.art_course.user.repository.UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderController(OrderService orderService, com.example.art_course.user.repository.UserRepository userRepository) {
        this.orderService = orderService;
        this.userRepository = userRepository;
    }


    @PostMapping("/checkout")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<List<Order>> checkout(
            Authentication authentication,
            @RequestBody OrderService.CheckoutRequest request
    ) {
        UUID parentId = resolveUserId(authentication);
        List<Order> orders = orderService.createOrdersFromCheckout(parentId, request);
        return ApiResponse.ok("Tạo đơn hàng thành công (tách theo từng giáo viên)", orders);
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<List<Order>> getMyOrders(Authentication authentication) {
        UUID parentId = resolveUserId(authentication);
        return ApiResponse.ok(orderService.getParentOrders(parentId));
    }

    @GetMapping("/teacher-orders")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<Order>> getTeacherOrders(Authentication authentication) {
        UUID teacherId = resolveUserId(authentication);
        return ApiResponse.ok(orderService.getTeacherOrders(teacherId));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER', 'STAFF', 'ADMIN')")
    public ApiResponse<OrderService.OrderDetailResponse> getOrderDetail(
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication);
        String role = resolveRole(authentication);
        return ApiResponse.ok(orderService.getOrderDetail(orderId, userId, role));
    }

    /**
     * Hủy đơn hàng chưa thanh toán (không có mã riêng, BR-11).
     */
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('PARENT', 'STAFF', 'ADMIN')")
    public ApiResponse<Void> cancelOrder(
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication);
        String role = resolveRole(authentication);
        orderService.cancelOrder(orderId, userId, role);
        return ApiResponse.ok("Đã hủy đơn hàng và trả lại tồn kho thành công", null);
    }

    private UUID resolveUserId(Authentication authentication) {
        if (authentication == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID u) return u;
        String name = authentication.getName();
        try {
            return UUID.fromString(name);
        } catch (Exception e) {
            if (userRepository != null) {
                return userRepository.findByUsername(name)
                        .map(com.example.art_course.user.entity.User::getId)
                        .orElse(null);
            }
            return null;
        }
    }

    private String resolveRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null || authentication.getAuthorities().isEmpty()) {
            return "PARENT";
        }
        return authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
    }
}
