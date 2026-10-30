package com.example.art_course.modules.finance.dto;

import com.example.art_course.modules.payment.entity.MoneyLedger;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherBalanceDto {
    private UUID teacherId;
    private BigDecimal heldBalance;      // Đang giữ (escrow)
    private BigDecimal availableBalance; // Có thể rút
    private BigDecimal pendingBalance;   // Đang chờ rút
    private BigDecimal withdrawnBalance; // Đã rút
    private List<MoneyLedger> history;   // Lịch sử biến động sổ cái
    private List<HeldOrder> heldOrders;

    public record HeldOrder(UUID orderId, BigDecimal amount) {}
}
