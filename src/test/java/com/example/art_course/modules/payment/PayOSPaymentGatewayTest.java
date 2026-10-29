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
import java.math.BigDecimal;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.order.entity.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import static org.junit.jupiter.api.Assertions.*;

class PayOSPaymentGatewayTest {

    private static final String KEY = "test-checksum-key";

    private PayOSPaymentGateway gateway() {
        PayOSPaymentGateway g = new PayOSPaymentGateway(org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class));
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

    @Test
    void paymentLinksUseDatabaseCodesAndQueryUsesActualPaidAmount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT nextval('public.payos_order_code_seq')", Long.class)).thenReturn(1001L, 1002L);
        PayOSPaymentGateway gateway = new PayOSPaymentGateway(jdbc);
        ReflectionTestUtils.setField(gateway, "clientId", "test-client");
        ReflectionTestUtils.setField(gateway, "apiKey", "test-api");
        ReflectionTestUtils.setField(gateway, "checksumKey", KEY);
        ReflectionTestUtils.setField(gateway, "endpoint", "https://payos.test");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(gateway, "http", builder.build());
        for (int code = 1001; code <= 1002; code++) {
            server.expect(requestTo("https://payos.test/v2/payment-requests"))
                    .andExpect(content().json("{\"orderCode\":" + code + ",\"amount\":50000}"))
                    .andRespond(withSuccess("{\"code\":\"00\",\"data\":{\"checkoutUrl\":\"https://payos.test/pay\"}}", MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo("https://payos.test/v2/payment-requests/1002"))
                .andRespond(withSuccess("{\"code\":\"00\",\"data\":{\"id\":\"pay-id\",\"status\":\"PAID\",\"amount\":50000,\"amountPaid\":30000}}", MediaType.APPLICATION_JSON));
        Payment payment = Payment.builder().amount(new BigDecimal("50000")).build();
        gateway.createPaymentUrl(Order.builder().build(), payment, "https://app.test/return");
        assertEquals(1001L, payment.getGatewayOrderCode());
        gateway.createPaymentUrl(Order.builder().build(), payment, "https://app.test/return");
        assertEquals(1002L, payment.getGatewayOrderCode());
        PaymentGateway.GatewayQueryResult result = gateway.queryTransaction(payment);
        assertTrue(result.paid());
        assertEquals(0, new BigDecimal("30000").compareTo(result.amount()));
        server.verify();
    }
}
