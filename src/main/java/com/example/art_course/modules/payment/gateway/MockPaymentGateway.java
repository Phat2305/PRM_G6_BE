package com.example.art_course.modules.payment.gateway;

import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.payment.entity.Payment;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("test")
public class MockPaymentGateway implements PaymentGateway {

    public static final String GATEWAY_CODE = "MOCK";
    private static final String MOCK_SECRET = "mock-gateway-secret-for-dev-tests-only";

    @Override
    public String getGatewayCode() {
        return GATEWAY_CODE;
    }

    @Override
    public String createPaymentUrl(Order order, Payment payment, String returnUrl) {
        return "https://mock-payment-gateway.local/checkout?paymentId=" + payment.getId()
                + "&amount=" + payment.getAmount()
                + "&orderId=" + order.getId()
                + "&returnUrl=" + returnUrl;
    }

    @Override
    public GatewayCallbackResult verifyCallback(Map<String, String> params) {
        String signature = params.get("signature");
        String txId = params.get("transactionId");
        String status = params.get("status");
        String amountStr = params.get("amount");

        boolean validSignature = signature != null && !signature.contains("invalid");
        boolean success = "00".equals(status) || "SUCCESS".equalsIgnoreCase(status);
        BigDecimal amount = amountStr != null ? new BigDecimal(amountStr) : BigDecimal.ZERO;

        return new GatewayCallbackResult(validSignature, success, txId != null ? txId : UUID.randomUUID().toString(), amount, "Mock callback processed");
    }

    @Override
    public GatewayQueryResult queryTransaction(Payment payment) {
        return new GatewayQueryResult(true, true, payment.getGatewayTransactionId(), payment.getAmount());
    }
}
