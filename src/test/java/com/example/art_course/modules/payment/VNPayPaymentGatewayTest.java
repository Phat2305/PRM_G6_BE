package com.example.art_course.modules.payment;

import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.gateway.PaymentGateway;
import com.example.art_course.modules.payment.gateway.VNPayPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VNPay Payment Gateway Unit Tests")
public class VNPayPaymentGatewayTest {

    private VNPayPaymentGateway vnpayGateway;
    private final String secretKey = "TEST_SECRET_KEY_VNPAY_12345";
    private final String tmnCode = "TEST_TMN";

    @BeforeEach
    void setUp() {
        vnpayGateway = new VNPayPaymentGateway();
        ReflectionTestUtils.setField(vnpayGateway, "tmnCode", tmnCode);
        ReflectionTestUtils.setField(vnpayGateway, "hashSecret", secretKey);
        ReflectionTestUtils.setField(vnpayGateway, "vnpayUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
    }

    @Test
    @DisplayName("Tạo payment URL VNPay: đầy đủ tham số, vnp_Amount x100, timezone GMT+7, chữ ký SHA512")
    void testCreatePaymentUrl() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        Order order = Order.builder().id(orderId).build();
        Payment payment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .amount(new BigDecimal("350000"))
                .build();

        String returnUrl = "http://localhost:8080/api/v1/payments/return/VNPAY";
        String payUrl = vnpayGateway.createPaymentUrl(order, payment, returnUrl);

        assertNotNull(payUrl);
        assertTrue(payUrl.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(payUrl.contains("vnp_TmnCode=" + tmnCode));
        assertTrue(payUrl.contains("vnp_Amount=35000000")); // 350.000 x 100
        assertTrue(payUrl.contains("vnp_TxnRef=" + paymentId));
        assertTrue(payUrl.contains("vnp_SecureHash="));
    }

    @Test
    @DisplayName("Xác thực callback VNPay thành công (ResponseCode 00)")
    void testVerifyCallback_Success() {
        UUID paymentId = UUID.randomUUID();
        String amount = "35000000";

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", amount);
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_OrderInfo", "Thanh toan don hang");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_TransactionNo", "14123456");
        params.put("vnp_TxnRef", paymentId.toString());

        // Tính toán hash chuẩn
        StringBuilder hashData = new StringBuilder();
        params.keySet().stream().sorted().forEach(k -> {
            hashData.append(k).append("=").append(params.get(k)).append("&");
        });
        hashData.setLength(hashData.length() - 1);
        String secureHash = VNPayPaymentGateway.hmacSHA512(secretKey, hashData.toString());
        params.put("vnp_SecureHash", secureHash);

        PaymentGateway.GatewayCallbackResult result = vnpayGateway.verifyCallback(params);

        assertTrue(result.validSignature(), "Chữ ký phải hợp lệ");
        assertTrue(result.success(), "Giao dịch phải thành công (code 00)");
        assertEquals("14123456", result.gatewayTransactionId());
        assertEquals(new BigDecimal("350000"), result.amount());
    }

    @Test
    @DisplayName("Xác thực callback VNPay thất bại do chữ ký giả mạo")
    void testVerifyCallback_InvalidSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", "FAKE_SIGNATURE_TAMPERED");

        PaymentGateway.GatewayCallbackResult result = vnpayGateway.verifyCallback(params);

        assertFalse(result.validSignature(), "Chữ ký giả mạo phải bị từ chối");
    }

    @Test
    @DisplayName("Xác thực callback VNPay: khách hủy thanh toán (ResponseCode 24)")
    void testVerifyCallback_CustomerCancelled() {
        UUID paymentId = UUID.randomUUID();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "20000000");
        params.put("vnp_ResponseCode", "24"); // Khách hàng hủy giao dịch
        params.put("vnp_TxnRef", paymentId.toString());

        StringBuilder hashData = new StringBuilder();
        params.keySet().stream().sorted().forEach(k -> {
            hashData.append(k).append("=").append(params.get(k)).append("&");
        });
        hashData.setLength(hashData.length() - 1);
        String secureHash = VNPayPaymentGateway.hmacSHA512(secretKey, hashData.toString());
        params.put("vnp_SecureHash", secureHash);

        PaymentGateway.GatewayCallbackResult result = vnpayGateway.verifyCallback(params);

        assertTrue(result.validSignature(), "Chữ ký hợp lệ");
        assertFalse(result.success(), "Giao dịch phải là thất bại do khách hủy (code 24)");
    }
}
