import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { Money } from '../src/common/money';
import {
  validateOrderTransition,
  validateDeliveryTransition,
  validatePaymentTransition,
  validateWithdrawalTransition,
} from '../src/common/state-machines';

describe('Unit Tests: Money & State Machines', () => {
  it('tính toán cộng trừ nhân chia chính xác với VND không bị sai số floating-point', () => {
    // 0.1 + 0.2 trong float thông thường = 0.30000000000000004
    const m1 = new Money('0.1');
    const m2 = new Money('0.2');
    assert.equal(m1.add(m2).toString(), '0'); // Làm tròn VND = 0

    const price = new Money(350000);
    const qty = 3;
    const subtotal = price.mul(qty);
    assert.equal(subtotal.toString(), '1050000');

    // Hoa hồng 20%
    const { commission, net } = subtotal.calculateCommission('0.20');
    assert.equal(commission.toString(), '210000');
    assert.equal(net.toString(), '840000');
    assert.equal(commission.add(net).toString(), subtotal.toString(), 'Bảo toàn tổng số tiền');
  });

  it('kiểm tra chuyển trạng thái hợp lệ và không hợp lệ của Order', () => {
    assert.equal(validateOrderTransition('PENDING_PAYMENT', 'PAID'), true);
    assert.equal(validateOrderTransition('PENDING_PAYMENT', 'EXPIRED'), true);
    assert.equal(validateOrderTransition('PENDING_PAYMENT', 'CANCELLED'), true);
    assert.equal(validateOrderTransition('PAID', 'EXPIRED'), false);
    assert.equal(validateOrderTransition('PAID', 'CANCELLED'), false);
  });

  it('kiểm tra chuyển trạng thái của Delivery', () => {
    assert.equal(validateDeliveryTransition('PENDING', 'CONFIRMED'), true);
    assert.equal(validateDeliveryTransition('CONFIRMED', 'SHIPPING'), true);
    assert.equal(validateDeliveryTransition('SHIPPING', 'DELIVERED'), true);
    assert.equal(validateDeliveryTransition('DELIVERED', 'COMPLETED'), true);
    assert.equal(validateDeliveryTransition('PENDING', 'COMPLETED'), false);
  });

  it('kiểm tra chuyển trạng thái của Payment', () => {
    assert.equal(validatePaymentTransition('PENDING', 'HELD'), true);
    assert.equal(validatePaymentTransition('HELD', 'RELEASED'), true);
    assert.equal(validatePaymentTransition('HELD', 'REFUNDED'), true);
    assert.equal(validatePaymentTransition('PENDING', 'RELEASED'), false);
  });

  it('kiểm tra chuyển trạng thái của Withdrawal', () => {
    assert.equal(validateWithdrawalTransition('PENDING', 'APPROVED'), true);
    assert.equal(validateWithdrawalTransition('APPROVED', 'PAID'), true);
    assert.equal(validateWithdrawalTransition('PENDING', 'PAID'), false);
  });
});
