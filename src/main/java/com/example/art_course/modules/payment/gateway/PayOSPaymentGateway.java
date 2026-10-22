package com.example.art_course.modules.payment.gateway;

import com.example.art_course.common.AppError;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.payment.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

// PAY-01: cổng PayOS (https://payos.vn/docs/api/)
@Component
public class PayOSPaymentGateway implements PaymentGateway {

    public static final String GATEWAY_CODE = "PAYOS";
    // Controller gộp webhook JSON thành params phẳng; các trường của "data" mang tiền tố này.
    public static final String DATA_PREFIX = "data.";

    @Value("${PAYOS_CLIENT_ID:${app.payment.payos.client-id:}}")
    private String clientId;

    @Value("${PAYOS_API_KEY:${app.payment.payos.api-key:}}")
    private String apiKey;

    @Value("${PAYOS_CHECKSUM_KEY:${app.payment.payos.checksum-key:}}")
    private String checksumKey;

    @Value("${PAYOS_ENDPOINT:${app.payment.payos.endpoint:https://api-merchant.payos.vn}}")
    private String endpoint;

    private final RestClient http = RestClient.create();

    @Override
    public String getGatewayCode() {
        return GATEWAY_CODE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String createPaymentUrl(Order order, Payment payment, String returnUrl) {
        requireConfigured();
        // orderCode của PayOS là số nguyên duy nhất; mỗi lần tạo link dùng mã mới
        long orderCode = System.currentTimeMillis();
        long amount = payment.getAmount().longValueExact();
        String description = "DH" + orderCode % 1_000_000_000L; // tối đa 25 ký tự
        // PayOS tự gắn status/cancel vào cả hai URL; client dựa vào đó để phân biệt
        String cancelUrl = returnUrl;

        String raw = "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description
                + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", description);
        body.put("returnUrl", returnUrl);
        body.put("cancelUrl", cancelUrl);
        body.put("signature", hmacSHA256(checksumKey, raw));

        Map<String, Object> res;
        try {
            res = http.post().uri(endpoint + "/v2/payment-requests")
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            throw new AppError(HttpStatus.BAD_GATEWAY, "PAYOS_ERROR", "Không kết nối được PayOS: " + ex.getMessage());
        }
        Map<String, Object> data = res == null ? null : (Map<String, Object>) res.get("data");
        if (res == null || !"00".equals(String.valueOf(res.get("code"))) || data == null) {
            throw new AppError(HttpStatus.BAD_GATEWAY, "PAYOS_ERROR",
                    "PayOS từ chối tạo link: " + (res == null ? "không có phản hồi" : res.get("desc")));
        }
        payment.setGatewayOrderCode(orderCode);
        return String.valueOf(data.get("checkoutUrl"));
    }

    @Override
    public GatewayCallbackResult verifyCallback(Map<String, String> params) {
        String signature = params.get("signature");
        if (signature == null || signature.isBlank()) {
            return new GatewayCallbackResult(false, false, null, BigDecimal.ZERO, "Thiếu chữ ký PayOS");
        }
        // Chữ ký = HMAC-SHA256 của các trường "data" sắp xếp theo tên: k1=v1&k2=v2
        TreeMap<String, String> data = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith(DATA_PREFIX)) data.put(k.substring(DATA_PREFIX.length()), v == null ? "" : v);
        });
        StringBuilder raw = new StringBuilder();
        data.forEach((k, v) -> raw.append(raw.length() == 0 ? "" : "&").append(k).append('=').append(v));
        boolean valid = MessageDigest.isEqual(
                hmacSHA256(checksumKey, raw.toString()).getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8));

        boolean success = "true".equalsIgnoreCase(params.get("success")) && "00".equals(data.get("code"));
        BigDecimal amount = BigDecimal.ZERO;
        try {
            amount = new BigDecimal(data.getOrDefault("amount", "0"));
        } catch (Exception ignored) {}
        String ref = data.get("reference");
        String txId = ref != null && !ref.isBlank() ? ref : data.get("paymentLinkId");
        return new GatewayCallbackResult(valid, success, txId, amount, params.getOrDefault("desc", ""));
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayQueryResult queryTransaction(Payment payment) {
        if (payment.getGatewayOrderCode() == null) {
            return new GatewayQueryResult(false, false, null, payment.getAmount());
        }
        requireConfigured();
        Map<String, Object> res;
        try {
            res = http.get().uri(endpoint + "/v2/payment-requests/" + payment.getGatewayOrderCode())
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            // Lỗi mạng: coi như chưa biết, để webhook hoặc lần đối soát sau xử lý
            return new GatewayQueryResult(false, false, null, payment.getAmount());
        }
        Map<String, Object> data = res == null ? null : (Map<String, Object>) res.get("data");
        if (data == null) {
            return new GatewayQueryResult(false, false, null, payment.getAmount());
        }
        boolean paid = "PAID".equals(String.valueOf(data.get("status")));
        return new GatewayQueryResult(true, paid, String.valueOf(data.get("id")), payment.getAmount());
    }

    private void requireConfigured() {
        if (isBlank(clientId) || isBlank(apiKey) || isBlank(checksumKey)) {
            throw new AppError(HttpStatus.INTERNAL_SERVER_ERROR, "PAYOS_NOT_CONFIGURED",
                    "Chưa cấu hình PAYOS_CLIENT_ID / PAYOS_API_KEY / PAYOS_CHECKSUM_KEY");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static String hmacSHA256(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder sb = new StringBuilder();
            for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
