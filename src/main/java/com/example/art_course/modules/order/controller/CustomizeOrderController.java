package com.example.art_course.modules.order.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.common.AppError;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.service.CustomizeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// CUS-08: phụ huynh lấy đơn chờ thanh toán cho yêu cầu đã chấp nhận báo giá, rồi thanh toán qua /api/v1/payments/create.
@RestController
@RequestMapping("/api/v1/orders/customize")
@RequiredArgsConstructor
public class CustomizeOrderController {

    private final CustomizeOrderService customizeOrderService;

    @PostMapping("/{requestId}")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<Order> orderFor(@PathVariable UUID requestId, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UUID parentId)) {
            throw new AppError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token không hợp lệ");
        }
        return ApiResponse.ok(customizeOrderService.orderFor(requestId, parentId));
    }
}
