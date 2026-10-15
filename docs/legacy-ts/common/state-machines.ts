export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'EXPIRED' | 'CANCELLED';

export type DeliveryStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED'
  | 'DELIVERY_FAILED'
  | 'REFUNDED';

export type PaymentStatus = 'PENDING' | 'HELD' | 'RELEASED' | 'FAILED' | 'REFUNDED';

export type WithdrawalStatus = 'PENDING' | 'APPROVED' | 'PAID' | 'REJECTED';

// ==========================================
// 1. ORDER STATE MACHINE
// ==========================================
export const ORDER_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  PENDING_PAYMENT: ['PAID', 'EXPIRED', 'CANCELLED'],
  PAID: [],
  EXPIRED: [],
  CANCELLED: [],
};

export function validateOrderTransition(current: OrderStatus, next: OrderStatus): boolean {
  return ORDER_TRANSITIONS[current]?.includes(next) ?? false;
}

// ==========================================
// 2. DELIVERY STATE MACHINE (SUPPLIES)
// ==========================================
export const DELIVERY_TRANSITIONS: Record<DeliveryStatus, DeliveryStatus[]> = {
  PENDING: ['CONFIRMED', 'REJECTED', 'CANCELLED'],
  CONFIRMED: ['SHIPPING', 'CANCELLED'],
  SHIPPING: ['DELIVERED', 'DELIVERY_FAILED'],
  DELIVERED: ['COMPLETED', 'REFUNDED'],
  COMPLETED: [],
  CANCELLED: [],
  REJECTED: [],
  DELIVERY_FAILED: ['SHIPPING', 'REFUNDED'],
  REFUNDED: [],
};

export function validateDeliveryTransition(current: DeliveryStatus, next: DeliveryStatus): boolean {
  return DELIVERY_TRANSITIONS[current]?.includes(next) ?? false;
}

// ==========================================
// 3. PAYMENT STATE MACHINE
// ==========================================
export const PAYMENT_TRANSITIONS: Record<PaymentStatus, PaymentStatus[]> = {
  PENDING: ['HELD', 'FAILED'],
  HELD: ['RELEASED', 'REFUNDED'],
  RELEASED: ['REFUNDED'],
  FAILED: [],
  REFUNDED: [],
};

export function validatePaymentTransition(current: PaymentStatus, next: PaymentStatus): boolean {
  return PAYMENT_TRANSITIONS[current]?.includes(next) ?? false;
}

// ==========================================
// 4. WITHDRAWAL REQUEST STATE MACHINE
// ==========================================
export const WITHDRAWAL_TRANSITIONS: Record<WithdrawalStatus, WithdrawalStatus[]> = {
  PENDING: ['APPROVED', 'REJECTED'],
  APPROVED: ['PAID', 'REJECTED'],
  PAID: [],
  REJECTED: [],
};

export function validateWithdrawalTransition(current: WithdrawalStatus, next: WithdrawalStatus): boolean {
  return WITHDRAWAL_TRANSITIONS[current]?.includes(next) ?? false;
}
