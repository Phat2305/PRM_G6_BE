package com.example.art_course.modules.finance.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.modules.systemconfig.entity.SystemConfig;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.modules.withdrawal.dto.MarkPaidRequest;
import com.example.art_course.modules.withdrawal.dto.RejectWithdrawalRequest;
import com.example.art_course.modules.withdrawal.dto.WithdrawalResponseDto;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final WithdrawalService withdrawalService;
    private final ConfigService configService;
    private final TeacherNames teacherNames;

    /**
     * Danh sách tất cả yêu cầu rút tiền (lọc theo trạng thái) (FIN-04).
     */
    @GetMapping("/withdrawals")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<List<WithdrawalResponseDto>> listWithdrawals(
            @RequestParam(required = false) WithdrawalStatus status
    ) {
        List<WithdrawalResponseDto> list = withdrawalService.getAllWithdrawals(status);
        Map<UUID, String> names = teacherNames.byIds(
                list.stream().map(WithdrawalResponseDto::getTeacherId).distinct().toList());
        list.forEach(dto -> dto.setTeacherName(names.get(dto.getTeacherId())));
        return ApiResponse.ok(list);
    }

    /**
     * Staff/Admin duyệt yêu cầu rút tiền (FIN-05).
     */
    @PostMapping("/withdrawals/{requestId}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<WithdrawalResponseDto> approveWithdrawal(
            @PathVariable UUID requestId,
            Authentication auth
    ) {
        UUID adminId = UUID.fromString(auth.getName());
        WithdrawalResponseDto dto = withdrawalService.approveWithdrawal(requestId, adminId);
        return ApiResponse.ok("Duyệt yêu cầu rút tiền thành công", dto);
    }

    /**
     * Staff/Admin từ chối yêu cầu rút tiền (FIN-05).
     */
    @PostMapping("/withdrawals/{requestId}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<WithdrawalResponseDto> rejectWithdrawal(
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectWithdrawalRequest req,
            Authentication auth
    ) {
        UUID adminId = UUID.fromString(auth.getName());
        WithdrawalResponseDto dto = withdrawalService.rejectWithdrawal(requestId, adminId, req);
        return ApiResponse.ok("Từ chối yêu cầu rút tiền thành công và đã hoàn trả số dư", dto);
    }

    /**
     * Staff/Admin chuyển khoản thủ công và đánh dấu PAID (FIN-06).
     */
    @PostMapping(value = "/withdrawals/{requestId}/paid", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<WithdrawalResponseDto> markPaid(
            @PathVariable UUID requestId,
            @Valid @RequestBody MarkPaidRequest req,
            Authentication auth
    ) {
        UUID staffId = UUID.fromString(auth.getName());
        WithdrawalResponseDto dto = withdrawalService.markPaidWithdrawal(requestId, staffId, req);
        return ApiResponse.ok("Xác nhận chi trả thành công", dto);
    }

    // FIN-06: xác nhận đã chi bằng ảnh, giữ API JSON cho client cũ.
    @PostMapping(value = "/withdrawals/{requestId}/paid", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<WithdrawalResponseDto> markPaidWithProof(@PathVariable UUID requestId,
            @RequestParam(value = "file", required = false) MultipartFile file, Authentication auth) {
        return ApiResponse.ok("Xác nhận chi trả thành công",
                withdrawalService.markPaidWithProof(requestId, UUID.fromString(auth.getName()), file));
    }

    /**
     * Lấy toàn bộ cấu hình hệ thống (ADM-02).
     */
    @GetMapping("/configs")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<List<SystemConfig>> getConfigs() {
        return ApiResponse.ok(configService.getAllConfigs());
    }

    /**
     * Admin cập nhật cấu hình hệ thống (hoa hồng, thời gian chờ) (ADM-02).
     */
    @PutMapping("/configs/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemConfig> updateConfig(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        UUID adminId = UUID.fromString(auth.getName());
        String value = body.get("value");
        SystemConfig updated = configService.updateConfig(key, value, adminId);
        return ApiResponse.ok("Cập nhật cấu hình thành công", updated);
    }
}
