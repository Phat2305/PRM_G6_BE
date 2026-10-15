package com.example.art_course.common.statemachine;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máy trạng thái tập trung cho BE2 (NFR-24 & SRS mục 8.1).
 * Mỗi bước chuyển trạng thái phải được kiểm tra hợp lệ; chuyển sai bị từ chối.
 */
public class StateMachineValidator {

    private static final Map<OrderStatus, Set<OrderStatus>> ORDER_TRANSITIONS = new EnumMap<>(OrderStatus.class);
    private static final Map<DeliveryStatus, Set<DeliveryStatus>> DELIVERY_TRANSITIONS = new EnumMap<>(DeliveryStatus.class);
    private static final Map<PaymentStatus, Set<PaymentStatus>> PAYMENT_TRANSITIONS = new EnumMap<>(PaymentStatus.class);
    private static final Map<WithdrawalStatus, Set<WithdrawalStatus>> WITHDRAWAL_TRANSITIONS = new EnumMap<>(WithdrawalStatus.class);

    static {
        // Order transitions
        ORDER_TRANSITIONS.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.PAID, OrderStatus.EXPIRED, OrderStatus.CANCELLED));
        ORDER_TRANSITIONS.put(OrderStatus.PAID, EnumSet.noneOf(OrderStatus.class));
        ORDER_TRANSITIONS.put(OrderStatus.EXPIRED, EnumSet.noneOf(OrderStatus.class));
        ORDER_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

        // Delivery transitions
        DELIVERY_TRANSITIONS.put(DeliveryStatus.PENDING, EnumSet.of(DeliveryStatus.CONFIRMED, DeliveryStatus.REJECTED, DeliveryStatus.CANCELLED));
        DELIVERY_TRANSITIONS.put(DeliveryStatus.CONFIRMED, EnumSet.of(DeliveryStatus.SHIPPING, DeliveryStatus.CANCELLED));
        DELIVERY_TRANSITIONS.put(DeliveryStatus.SHIPPING, EnumSet.of(DeliveryStatus.DELIVERED, DeliveryStatus.DELIVERY_FAILED));
        DELIVERY_TRANSITIONS.put(DeliveryStatus.DELIVERED, EnumSet.of(DeliveryStatus.COMPLETED, DeliveryStatus.REFUNDED));
        DELIVERY_TRANSITIONS.put(DeliveryStatus.COMPLETED, EnumSet.noneOf(DeliveryStatus.class));
        DELIVERY_TRANSITIONS.put(DeliveryStatus.CANCELLED, EnumSet.noneOf(DeliveryStatus.class));
        DELIVERY_TRANSITIONS.put(DeliveryStatus.REJECTED, EnumSet.noneOf(DeliveryStatus.class));
        DELIVERY_TRANSITIONS.put(DeliveryStatus.DELIVERY_FAILED, EnumSet.of(DeliveryStatus.SHIPPING, DeliveryStatus.REFUNDED));
        DELIVERY_TRANSITIONS.put(DeliveryStatus.REFUNDED, EnumSet.noneOf(DeliveryStatus.class));

        // Payment transitions
        PAYMENT_TRANSITIONS.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.HELD, PaymentStatus.FAILED));
        PAYMENT_TRANSITIONS.put(PaymentStatus.HELD, EnumSet.of(PaymentStatus.RELEASED, PaymentStatus.REFUNDED));
        PAYMENT_TRANSITIONS.put(PaymentStatus.RELEASED, EnumSet.of(PaymentStatus.REFUNDED));
        PAYMENT_TRANSITIONS.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));
        PAYMENT_TRANSITIONS.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));

        // Withdrawal transitions
        WITHDRAWAL_TRANSITIONS.put(WithdrawalStatus.PENDING, EnumSet.of(WithdrawalStatus.APPROVED, WithdrawalStatus.REJECTED));
        WITHDRAWAL_TRANSITIONS.put(WithdrawalStatus.APPROVED, EnumSet.of(WithdrawalStatus.PAID, WithdrawalStatus.REJECTED));
        WITHDRAWAL_TRANSITIONS.put(WithdrawalStatus.PAID, EnumSet.noneOf(WithdrawalStatus.class));
        WITHDRAWAL_TRANSITIONS.put(WithdrawalStatus.REJECTED, EnumSet.noneOf(WithdrawalStatus.class));
    }

    public static boolean isValidOrderTransition(OrderStatus current, OrderStatus next) {
        return current != null && next != null && ORDER_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    public static boolean isValidDeliveryTransition(DeliveryStatus current, DeliveryStatus next) {
        return current != null && next != null && DELIVERY_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    public static boolean isValidPaymentTransition(PaymentStatus current, PaymentStatus next) {
        return current != null && next != null && PAYMENT_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    public static boolean isValidWithdrawalTransition(WithdrawalStatus current, WithdrawalStatus next) {
        return current != null && next != null && WITHDRAWAL_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }
}
