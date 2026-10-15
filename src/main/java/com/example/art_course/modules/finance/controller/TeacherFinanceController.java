package com.example.art_course.modules.finance.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.finance.dto.TeacherBalanceDto;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.withdrawal.dto.CreateWithdrawalRequest;
import com.example.art_course.modules.withdrawal.dto.WithdrawalResponseDto;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/teacher/finance")
@RequiredArgsConstructor
public class TeacherFinanceController {

    private final LedgerService ledgerService;
    private final WithdrawalService withdrawalService;

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
