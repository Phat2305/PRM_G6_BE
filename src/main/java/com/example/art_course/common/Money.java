package com.example.art_course.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Lớp tính toán tiền tệ VND chuẩn xác (Quy tắc kỹ thuật #1 & Quyết định #5).
 * Sử dụng BigDecimal với RoundingMode.HALF_UP, đơn vị VND nguyên tệ (không có phần thập phân).
 * Đảm bảo bảo toàn tuyệt đối: gross = commission + net.
 */
public class Money implements Comparable<Money> {
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal amount;

    public Money(BigDecimal amount) {
        this.amount = amount != null ? amount.setScale(0, ROUNDING_MODE) : BigDecimal.ZERO;
    }

    public Money(long amount) {
        this(BigDecimal.valueOf(amount));
    }

    public Money(String amount) {
        this(amount != null && !amount.trim().isEmpty() ? new BigDecimal(amount.trim()) : BigDecimal.ZERO);
    }

    public static Money of(long amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(amount);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other Money must not be null");
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "other Money must not be null");
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(long factor) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)));
    }

    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor must not be null");
        return new Money(this.amount.multiply(factor).setScale(0, ROUNDING_MODE));
    }

    /**
     * Tính hoa hồng và tiền thực nhận theo tỷ lệ (ví dụ: 0.20 cho 20%).
     * Hoa hồng = Round(gross * rate, 0, HALF_UP).
     * Thực nhận = gross - commission (bảo toàn tổng tiền).
     */
    public CommissionResult calculateCommission(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate must not be null");
        BigDecimal commissionAmount = this.amount.multiply(rate).setScale(0, ROUNDING_MODE);
        BigDecimal netAmount = this.amount.subtract(commissionAmount);
        return new CommissionResult(new Money(commissionAmount), new Money(netAmount));
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return this.compareTo(other) >= 0;
    }

    public boolean isLessThan(Money other) {
        return this.compareTo(other) < 0;
    }

    @Override
    public int compareTo(Money o) {
        return this.amount.compareTo(o.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }

    public record CommissionResult(Money commission, Money net) {}
}
