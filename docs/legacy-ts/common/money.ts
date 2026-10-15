import Decimal from 'decimal.js';

Decimal.set({
  precision: 28,
  rounding: Decimal.ROUND_HALF_UP,
});

/**
 * Lớp xử lý tiền tệ VND chuẩn xác (Quy tắc kỹ thuật #1)
 * Bảo đảm tuyệt đối không xảy ra lỗi floating point nhị phân (0.1 + 0.2 != 0.3).
 */
export class Money {
  private readonly value: Decimal;

  constructor(val: string | number | Decimal | Money) {
    if (val instanceof Money) {
      this.value = val.value;
    } else if (val instanceof Decimal) {
      this.value = val;
    } else {
      this.value = new Decimal(val || 0);
    }
  }

  static zero(): Money {
    return new Money(0);
  }

  add(other: Money | string | number): Money {
    const o = new Money(other);
    return new Money(this.value.plus(o.value));
  }

  sub(other: Money | string | number): Money {
    const o = new Money(other);
    return new Money(this.value.minus(o.value));
  }

  mul(factor: string | number | Decimal): Money {
    return new Money(this.value.times(new Decimal(factor)));
  }

  div(divisor: string | number | Decimal): Money {
    return new Money(this.value.dividedBy(new Decimal(divisor)));
  }

  /**
   * Làm tròn số nguyên VND theo quy tắc ROUND_HALF_UP (nửa trên làm tròn lên)
   */
  roundVND(): Money {
    return new Money(this.value.toDecimalPlaces(0, Decimal.ROUND_HALF_UP));
  }

  /**
   * Tính tiền hoa hồng theo tỷ lệ (ví dụ: 0.20 cho 20%)
   * Hoa hồng = Round(gross * rate, 0, ROUND_HALF_UP)
   */
  calculateCommission(rate: string | number): { commission: Money; net: Money } {
    const r = new Decimal(rate);
    const comm = this.value.times(r).toDecimalPlaces(0, Decimal.ROUND_HALF_UP);
    const net = this.value.minus(comm).toDecimalPlaces(0, Decimal.ROUND_HALF_UP);
    return {
      commission: new Money(comm),
      net: new Money(net),
    };
  }

  isZero(): boolean {
    return this.value.isZero();
  }

  isPositive(): boolean {
    return this.value.isPositive() && !this.value.isZero();
  }

  isNegative(): boolean {
    return this.value.isNegative();
  }

  gte(other: Money | string | number): boolean {
    return this.value.greaterThanOrEqualTo(new Money(other).value);
  }

  lte(other: Money | string | number): boolean {
    return this.value.lessThanOrEqualTo(new Money(other).value);
  }

  gt(other: Money | string | number): boolean {
    return this.value.greaterThan(new Money(other).value);
  }

  lt(other: Money | string | number): boolean {
    return this.value.lessThan(new Money(other).value);
  }

  equals(other: Money | string | number): boolean {
    return this.value.equals(new Money(other).value);
  }

  toString(): string {
    return this.roundVND().value.toFixed(0);
  }

  toNumber(): number {
    return this.roundVND().value.toNumber();
  }
}
