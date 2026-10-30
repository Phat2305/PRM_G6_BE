package com.example.art_course.modules.admin.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.modules.admin.dto.AdminAnalyticsDto;
import com.example.art_course.modules.admin.dto.LedgerAuditEntryDto;
import com.example.art_course.modules.admin.service.AdminService;
import com.example.art_course.modules.admin.service.AdminAuditDetailsService;
import com.example.art_course.modules.payment.entity.MoneyLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminService adminService;
    private final TeacherNames teacherNames;
    private final com.example.art_course.modules.finance.service.RevenueService revenueService;
    private final AdminAuditDetailsService auditDetails;

    /**
     * Báo cáo thống kê tài chính và vận hành sàn (ADM-04, ADM-05, ADM-06).
     */
    @GetMapping("/finance")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminAnalyticsDto> getFinanceAnalytics() {
        return ApiResponse.ok(adminService.getFinanceAnalytics());
    }

    /**
     * Doanh thu toàn sàn theo tháng cho biểu đồ (ADM-04); months 1–24, mặc định 6.
     */
    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<com.example.art_course.modules.finance.service.RevenueService.Point>> getRevenue(
            @RequestParam(defaultValue = "6") int months) {
        return ApiResponse.ok(revenueService.platformRevenue(months));
    }

    /**
     * Tra cứu sổ cái tài chính toàn sàn (Audit log - ADM-04), mới nhất trước, phân trang (page từ 0, size 1-200).
     */
    @GetMapping("/ledger-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<LedgerAuditEntryDto>> getLedgerAudit(
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String entryType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        List<MoneyLedger> entries = adminService.getLedgerAuditLogs(
                teacherId, entryType, Math.max(0, page), Math.min(Math.max(1, size), 200));
        Map<UUID, String> names = teacherNames.byIds(
                entries.stream().map(MoneyLedger::getTeacherId).distinct().toList());
        var details = auditDetails.ledgerDetails(entries);
        return ApiResponse.ok(entries.stream()
                .map(m -> LedgerAuditEntryDto.of(m, names.get(m.getTeacherId()), details.get(m.getId())))
                .toList());
    }
}
