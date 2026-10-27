package com.example.art_course.modules.finance.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Doanh thu theo thời gian (FIN-03, ADM-04): tính từ các giao dịch đã thanh toán (HELD hoặc RELEASED), chia ngày/tháng theo giờ Việt Nam.
@Service
@RequiredArgsConstructor
public class RevenueService {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<PaymentStatus> PAID = List.of(PaymentStatus.HELD, PaymentStatus.RELEASED);

    private final PaymentRepository paymentRepository;
    private Clock clock = Clock.system(ZONE);

    // Dùng trong test để cố định "hôm nay".
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public record Point(String period, BigDecimal amount) {}

    // Tiền thực nhận (sau hoa hồng) của giáo viên: WEEK = 7 ngày gần nhất (yyyy-MM-dd), MONTH = 6 tháng gần nhất (yyyy-MM).
    @Transactional(readOnly = true)
    public List<Point> teacherRevenue(UUID teacherId, String range) {
        boolean week = parseRange(range);
        return series(paymentRepository.findPaidByTeacherSince(teacherId, PAID, start(week, 7, 6)),
                week, 7, 6, Payment::getNetAmount);
    }

    // Tổng thanh toán của sàn theo tháng; months 1–24.
    @Transactional(readOnly = true)
    public List<Point> platformRevenue(int months) {
        if (months < 1 || months > 24) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_MONTHS", "months phải từ 1 đến 24");
        }
        return series(paymentRepository.findPaidSince(PAID, start(false, 7, months)),
                false, 7, months, Payment::getAmount);
    }

    private static boolean parseRange(String range) {
        if ("WEEK".equalsIgnoreCase(range)) {
            return true;
        }
        if ("MONTH".equalsIgnoreCase(range)) {
            return false;
        }
        throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "range phải là WEEK hoặc MONTH");
    }

    private Instant start(boolean week, int days, int months) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = week ? today.minusDays(days - 1L) : today.withDayOfMonth(1).minusMonths(months - 1L);
        return from.atStartOfDay(ZONE).toInstant();
    }

    private List<Point> series(List<Payment> payments, boolean week, int days, int months,
                               java.util.function.Function<Payment, BigDecimal> value) {
        LocalDate today = LocalDate.now(clock);
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        if (week) {
            for (int i = days - 1; i >= 0; i--) {
                buckets.put(today.minusDays(i).toString(), BigDecimal.ZERO);
            }
        } else {
            YearMonth current = YearMonth.from(today);
            for (int i = months - 1; i >= 0; i--) {
                buckets.put(current.minusMonths(i).toString(), BigDecimal.ZERO);
            }
        }
        for (Payment p : payments) {
            Instant at = p.getPaidAt() != null ? p.getPaidAt() : p.getCreatedAt();
            LocalDate day = at.atZone(ZONE).toLocalDate();
            String key = week ? day.toString() : YearMonth.from(day).toString();
            buckets.computeIfPresent(key, (k, v) -> v.add(value.apply(p)));
        }
        List<Point> points = new ArrayList<>();
        buckets.forEach((k, v) -> points.add(new Point(k, v)));
        return points;
    }
}
