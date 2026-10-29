package com.example.art_course.modules.payment.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.common.AppError;
import com.example.art_course.modules.payment.dto.CreatePaymentRequest;
import com.example.art_course.modules.payment.gateway.PayOSPaymentGateway;
import com.example.art_course.modules.payment.dto.PaymentResponseDto;
import com.example.art_course.modules.payment.dto.PaymentStatusResponseDto;
import com.example.art_course.modules.payment.service.PaymentService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    public PaymentController(PaymentService paymentService, UserRepository userRepository) {
        this.paymentService = paymentService;
        this.userRepository = userRepository;
    }


    /**
     * Tạo payment request và link thanh toán PayOS (PAY-01).
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<PaymentResponseDto> createPaymentUrl(
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication auth
    ) {
        UUID parentId = resolveUserId(auth);
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
        UUID userId = resolveUserId(auth);
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse("PARENT");

        PaymentStatusResponseDto status = paymentService.queryPaymentStatus(paymentId, userId, role);
        return ApiResponse.success(status);
    }

    /**
     * Webhook PayOS: body JSON lồng {code, desc, success, data{...}, signature}.
     * Làm phẳng thành params; các trường của data mang tiền tố "data." (PAY-01, PAY-09).
     */
    @PostMapping("/webhook/PAYOS")
    public ResponseEntity<Map<String, Object>> handlePayOSWebhook(@RequestBody Map<String, Object> body) {
        Map<String, String> params = new HashMap<>();
        params.put("signature", str(body.get("signature")));
        params.put("success", str(body.get("success")));
        params.put("desc", str(body.get("desc")));
        if (body.get("data") instanceof Map<?, ?> data) {
            data.forEach((k, v) -> params.put(PayOSPaymentGateway.DATA_PREFIX + k, str(v)));
        }
        return ResponseEntity.ok(paymentService.handleWebhook(PayOSPaymentGateway.GATEWAY_CODE, params));
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private UUID resolveUserId(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID u) return u;
        String name = auth.getName();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            if (userRepository != null) {
                return userRepository.findByUsername(name)
                        .map(User::getId)
                        .orElseThrow(() -> new AppError(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng: " + name));
            }
            throw new AppError(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng: " + name);
        }
    }
}
