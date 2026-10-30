package com.example.art_course.modules.finance.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.finance.dto.TeacherBalanceDto;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.finance.service.RevenueService;
import com.example.art_course.modules.withdrawal.dto.CreateWithdrawalRequest;
import com.example.art_course.modules.withdrawal.dto.WithdrawalResponseDto;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/teacher/finance")
@RequiredArgsConstructor
public class TeacherFinanceController {

    private final LedgerService ledgerService;
    private final WithdrawalService withdrawalService;
    private final RevenueService revenueService;

    // FIN-06, NFR-04: kiểm tra chủ yêu cầu trước khi đọc ảnh từ R2.
    @GetMapping("/withdrawals/{requestId}/proof")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF', 'ADMIN')")
    public ResponseEntity<Resource> proof(@PathVariable UUID requestId, Authentication auth) {
        boolean reviewer = auth.getAuthorities().stream()
                .anyMatch(a -> Set.of("ROLE_STAFF", "ROLE_ADMIN").contains(a.getAuthority()));
        var object = withdrawalService.proof(requestId, UUID.fromString(auth.getName()), reviewer);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(object.response().contentType()))
                .header("X-Content-Type-Options", "nosniff").header("Cache-Control", "private, no-store")
                .body(new InputStreamResource(object));
    }

    /**
     * Tra cứu số dư thời gian thực của Teacher (FIN-03).
     */
    @GetMapping("/balance")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<TeacherBalanceDto> getBalance(Authentication auth) {
        UUID teacherId = UUID.fromString(auth.getName());
        TeacherBalanceDto balance = ledgerService.getTeacherBalance(teacherId);
        return ApiResponse.ok(balance);
    }

    /**
     * Doanh thu thực nhận theo ngày/tháng cho biểu đồ (FIN-03): range=WEEK (7 ngày) hoặc MONTH (6 tháng).
     */
    @GetMapping("/revenue")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<RevenueService.Point>> getRevenue(
            @RequestParam(defaultValue = "WEEK") String range, Authentication auth) {
        return ApiResponse.ok(revenueService.teacherRevenue(UUID.fromString(auth.getName()), range));
    }

    /**
     * Teacher gửi yêu cầu rút tiền (FIN-04, BR-15).
     */
    @PostMapping("/withdrawals")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<WithdrawalResponseDto> requestWithdrawal(
            @Valid @RequestBody CreateWithdrawalRequest request,
            Authentication auth
    ) {
        UUID teacherId = UUID.fromString(auth.getName());
        WithdrawalResponseDto dto = withdrawalService.requestWithdrawal(teacherId, request);
        return ApiResponse.ok("Gửi yêu cầu rút tiền thành công", dto);
    }

    /**
     * Lịch sử các yêu cầu rút tiền của Teacher.
     */
    @GetMapping("/withdrawals")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<WithdrawalResponseDto>> getWithdrawals(Authentication auth) {
        UUID teacherId = UUID.fromString(auth.getName());
        List<WithdrawalResponseDto> list = withdrawalService.getTeacherWithdrawals(teacherId);
        return ApiResponse.ok(list);
    }
}
