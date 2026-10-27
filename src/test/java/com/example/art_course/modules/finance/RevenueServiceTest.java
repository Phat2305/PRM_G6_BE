package com.example.art_course.modules.finance;

import com.example.art_course.common.AppError;
import com.example.art_course.modules.finance.service.RevenueService;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Doanh thu: cộng theo ngày/tháng giờ Việt Nam, đủ các kỳ kể cả kỳ bằng 0")
class RevenueServiceTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RevenueService service = new RevenueService(payments);
    private final UUID teacherId = UUID.randomUUID();

    private Payment paid(String isoUtc, long amount, long net) {
        Payment p = new Payment();
        p.setPaidAt(Instant.parse(isoUtc));
        p.setAmount(BigDecimal.valueOf(amount));
        p.setNetAmount(BigDecimal.valueOf(net));
        return p;
    }

    @Test
    @DisplayName("tuần: 7 ngày; thanh toán 23:30 UTC ngày 26 là sáng 27 giờ Việt Nam")
    void weekBucketsUseVietnamTime() {
        // 27/10/2026 12:00 giờ VN
        service.setClock(Clock.fixed(Instant.parse("2026-10-27T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        when(payments.findPaidByTeacherSince(any(), any(), any())).thenReturn(List.of(
                paid("2026-10-26T23:30:00Z", 100000, 80000),
                paid("2026-10-27T02:00:00Z", 50000, 40000),
                paid("2026-10-20T10:00:00Z", 999, 999)));

        var points = service.teacherRevenue(teacherId, "WEEK");

        assertEquals(7, points.size());
        assertEquals("2026-10-21", points.get(0).period());
        assertEquals("2026-10-27", points.get(6).period());
        assertEquals(0, points.get(6).amount().compareTo(BigDecimal.valueOf(120000)));
        assertEquals(0, points.get(5).amount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("sàn theo tháng cộng tổng thanh toán; months ngoài 1–24 bị từ chối")
    void platformMonths() {
        service.setClock(Clock.fixed(Instant.parse("2026-10-27T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        when(payments.findPaidSince(any(), any())).thenReturn(List.of(
                paid("2026-10-05T10:00:00Z", 100000, 80000),
                paid("2026-09-30T20:00:00Z", 70000, 56000)));

        var points = service.platformRevenue(3);

        assertEquals(List.of("2026-08", "2026-09", "2026-10"),
                points.stream().map(RevenueService.Point::period).toList());
        // 20:00 UTC ngày 30/9 = 03:00 ngày 1/10 giờ VN
        assertEquals(0, points.get(2).amount().compareTo(BigDecimal.valueOf(170000)));
        assertThrows(AppError.class, () -> service.platformRevenue(0));
        assertThrows(AppError.class, () -> service.teacherRevenue(teacherId, "YEAR"));
    }
}
