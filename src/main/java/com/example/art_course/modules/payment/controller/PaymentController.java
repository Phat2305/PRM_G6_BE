package com.example.art_course.modules.payment.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.payment.dto.CreatePaymentRequest;
import com.example.art_course.modules.payment.dto.PaymentResponseDto;
import com.example.art_course.modules.payment.dto.PaymentStatusResponseDto;
import com.example.art_course.modules.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Tạo payment request và link thanh toán cổng VNPay/MoMo (PAY-01).
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<PaymentResponseDto> createPaymentUrl(
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication auth
    ) {
        UUID parentId = UUID.fromString(auth.getName());
        PaymentResponseDto response = paymentService.createPaymentUrl(parentId, request);
        return ApiResponse.success(response);
    }

    /**
     * Truy vấn chủ động trạng thái giao dịch (PAY-10).
     */
    @GetMapping("/{paymentId}/status")
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER', 'STAFF', 'ADMIN')")
    public ApiResponse<PaymentStatusResponseDto> getPaymentStatus(
            @PathVariable UUID paymentId,
            Authentication auth
    ) {
        UUID userId = UUID.fromString(auth.getName());
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse("PARENT");

        PaymentStatusResponseDto status = paymentService.queryPaymentStatus(paymentId, userId, role);
        return ApiResponse.success(status);
    }

    /**
     * Webhook / IPN xử lý kết quả thanh toán từ cổng qua POST (PAY-01, PAY-02, PAY-09).
     */
    @PostMapping("/webhook/{gateway}")
    public ResponseEntity<Map<String, Object>> handleWebhookPost(
            @PathVariable String gateway,
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestBody(required = false) Map<String, String> bodyParams
    ) {
        Map<String, String> allParams = new HashMap<>();
        if (queryParams != null) allParams.putAll(queryParams);
        if (bodyParams != null) allParams.putAll(bodyParams);

        Map<String, Object> result = paymentService.handleWebhook(gateway, allParams);
        return ResponseEntity.ok(result);
    }

    /**
     * Webhook / IPN hỗ trợ GET (cho các cổng hỗ trợ GET callback như VNPay).
     */
    @GetMapping("/webhook/{gateway}")
    public ResponseEntity<Map<String, Object>> handleWebhookGet(
            @PathVariable String gateway,
            @RequestParam Map<String, String> queryParams
    ) {
        Map<String, Object> result = paymentService.handleWebhook(gateway, queryParams);
        return ResponseEntity.ok(result);
    }

    /**
     * Return URL khi người dùng hoàn tất thanh toán và được điều hướng về app.
     */
    @GetMapping("/return/{gateway}")
    public ResponseEntity<Map<String, Object>> handleReturn(
            @PathVariable String gateway,
            @RequestParam Map<String, String> queryParams
    ) {
        Map<String, Object> result = paymentService.handleWebhook(gateway, queryParams);
        return ResponseEntity.ok(result);
    }
}
