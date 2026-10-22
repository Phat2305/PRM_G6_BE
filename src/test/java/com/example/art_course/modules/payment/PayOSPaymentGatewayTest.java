package com.example.art_course.modules.payment;

import com.example.art_course.modules.payment.gateway.PayOSPaymentGateway;
import com.example.art_course.modules.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayOSPaymentGatewayTest {

    private static final String KEY = "test-checksum-key";

    private PayOSPaymentGateway gateway() {
        PayOSPaymentGateway g = new PayOSPaymentGateway();
        ReflectionTestUtils.setField(g, "checksumKey", KEY);
        return g;
    }

    private static String sign(String raw) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder sb = new StringBuilder();
        for (byte b : mac.doFinal(raw.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private Map<String, String> webhook(String signature) {
        Map<String, String> p = new HashMap<>();
        p.put("success", "true");
        p.put("signature", signature);
        p.put("data.orderCode", "1700000000000");
        p.put("data.amount", "50000");
        p.put("data.code", "00");
        p.put("data.reference", "FT123");
        p.put("data.description", "");
        return p;
    }

    // Khóa sắp theo bảng chữ cái: amount, code, description, orderCode, reference
    private static final String RAW = "amount=50000&code=00&description=&orderCode=1700000000000&reference=FT123";

    @Test
    void validSignatureIsAccepted() throws Exception {
        PaymentGateway.GatewayCallbackResult r = gateway().verifyCallback(webhook(sign(RAW)));
        assertTrue(r.validSignature());
        assertTrue(r.success());
        assertEquals("FT123", r.gatewayTransactionId());
        assertEquals(0, r.amount().compareTo(new java.math.BigDecimal("50000")));
    }

    @Test
    void tamperedAmountIsRejected() throws Exception {
        Map<String, String> p = webhook(sign(RAW));
        p.put("data.amount", "1");
        assertFalse(gateway().verifyCallback(p).validSignature());
    }

    @Test
    void missingSignatureIsRejected() {
        Map<String, String> p = webhook(null);
        p.remove("signature");
        assertFalse(gateway().verifyCallback(p).validSignature());
    }
}
