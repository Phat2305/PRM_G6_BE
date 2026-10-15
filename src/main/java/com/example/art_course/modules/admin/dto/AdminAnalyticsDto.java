package com.example.art_course.modules.admin.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAnalyticsDto {
    private BigDecimal totalRevenue;
    private BigDecimal totalHeld;
    private BigDecimal totalAvailable;
    private BigDecimal totalPendingWithdrawals;
    private BigDecimal totalPaidOut;
    private BigDecimal totalRefunded;
    private BigDecimal totalCommission;
    private long totalOrders;
    private long paidOrders;
}
