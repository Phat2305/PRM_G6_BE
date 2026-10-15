package com.example.art_course.modules.admin.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.admin.dto.AdminAnalyticsDto;
import com.example.art_course.modules.admin.service.AdminService;
import com.example.art_course.modules.payment.entity.MoneyLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminService adminService;

    /**
     * Báo cáo thống kê tài chính và vận hành sàn (ADM-04, ADM-05, ADM-06).
     */
    @GetMapping("/finance")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminAnalyticsDto> getFinanceAnalytics() {
        return ApiResponse.ok(adminService.getFinanceAnalytics());
    }

    /**
     * Tra cứu sổ cái tài chính toàn sàn (Audit log - ADM-04).
     */
    @GetMapping("/ledger-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MoneyLedger>> getLedgerAudit(
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String entryType
    ) {
        return ApiResponse.ok(adminService.getLedgerAuditLogs(teacherId, entryType));
    }
}
