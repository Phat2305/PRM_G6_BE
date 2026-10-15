package com.example.art_course.modules.payment.gateway;

import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.payment.entity.Payment;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {
    String getGatewayCode();

    String createPaymentUrl(Order order, Payment payment, String returnUrl);

    GatewayCallbackResult verifyCallback(Map<String, String> params);

    GatewayQueryResult queryTransaction(Payment payment);

    record GatewayCallbackResult(
            boolean validSignature,
            boolean success,
            String gatewayTransactionId,
            BigDecimal amount,
            String message
    ) {}

    record GatewayQueryResult(
            boolean exists,
            boolean paid,
            String gatewayTransactionId,
            BigDecimal amount
    ) {}
}
