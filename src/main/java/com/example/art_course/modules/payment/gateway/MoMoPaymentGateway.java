package com.example.art_course.modules.payment.gateway;

import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.payment.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class MoMoPaymentGateway implements PaymentGateway {

    public static final String GATEWAY_CODE = "MOMO";

    @Value("${MOMO_PARTNER_CODE:${app.payment.momo.partner-code:}}")
    private String partnerCode;

    @Value("${MOMO_ACCESS_KEY:${app.payment.momo.access-key:}}")
    private String accessKey;

    @Value("${MOMO_SECRET_KEY:${app.payment.momo.secret-key:}}")
    private String secretKey;

    @Value("${MOMO_ENDPOINT:${app.payment.momo.endpoint:https://test-payment.momo.vn/v2/gateway/api/create}}")
    private String momoEndpoint;

    @Override
    public String getGatewayCode() {
        return GATEWAY_CODE;
    }

    @Override
    public String createPaymentUrl(Order order, Payment payment, String returnUrl) {
        String pCode = partnerCode != null && !partnerCode.isBlank() ? partnerCode : "MOMO_PARTNER_DEMO";
        String aKey = accessKey != null && !accessKey.isBlank() ? accessKey : "MOMO_ACCESS_DEMO";
        String sKey = secretKey != null && !secretKey.isBlank() ? secretKey : "MOMO_SECRET_DEMO";

        String orderId = payment.getId().toString();
        String requestId = orderId;
        String amount = payment.getAmount().toPlainString();
        String orderInfo = "Thanh toan don hang " + order.getId();
        String notifyUrl = returnUrl;

        String rawSignature = "accessKey=" + aKey
                + "&amount=" + amount
                + "&extraData="
                + "&ipnUrl=" + notifyUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + pCode
                + "&redirectUrl=" + returnUrl
                + "&requestId=" + requestId
                + "&requestType=captureWallet";

        String signature = hmacSHA256(sKey, rawSignature);

        return momoEndpoint + "?partnerCode=" + pCode
                + "&orderId=" + orderId
                + "&amount=" + amount
                + "&signature=" + signature;
    }

    @Override
    public GatewayCallbackResult verifyCallback(Map<String, String> params) {
        String signature = params.get("signature");
        if (signature == null) {
            return new GatewayCallbackResult(false, false, null, BigDecimal.ZERO, "Thiếu chữ ký MoMo signature");
        }

        String sKey = secretKey != null && !secretKey.isBlank() ? secretKey : "MOMO_SECRET_DEMO";
        String accessKeyVal = params.getOrDefault("accessKey", "");
        String amountVal = params.getOrDefault("amount", "0");
        String extraData = params.getOrDefault("extraData", "");
        String message = params.getOrDefault("message", "");
        String orderId = params.getOrDefault("orderId", "");
        String orderInfo = params.getOrDefault("orderInfo", "");
        String orderType = params.getOrDefault("orderType", "");
        String partnerCodeVal = params.getOrDefault("partnerCode", "");
        String payType = params.getOrDefault("payType", "");
        String requestId = params.getOrDefault("requestId", "");
        String responseTime = params.getOrDefault("responseTime", "");
        String resultCode = params.getOrDefault("resultCode", "");
        String transId = params.getOrDefault("transId", "");

        String rawSignature = "accessKey=" + accessKeyVal
                + "&amount=" + amountVal
                + "&extraData=" + extraData
                + "&message=" + message
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&orderType=" + orderType
                + "&partnerCode=" + partnerCodeVal
                + "&payType=" + payType
                + "&requestId=" + requestId
                + "&responseTime=" + responseTime
                + "&resultCode=" + resultCode
                + "&transId=" + transId;

        String calculatedHash = hmacSHA256(sKey, rawSignature);
        boolean validSignature = signature.equalsIgnoreCase(calculatedHash);
        boolean success = "0".equals(resultCode);

        BigDecimal amount = BigDecimal.ZERO;
        try {
            amount = new BigDecimal(amountVal);
        } catch (Exception ignored) {}

        return new GatewayCallbackResult(validSignature, success, transId, amount, message);
    }

    @Override
    public GatewayQueryResult queryTransaction(Payment payment) {
        return new GatewayQueryResult(true, true, payment.getGatewayTransactionId(), payment.getAmount());
    }

    public static String hmacSHA256(String key, String data) {
        try {
            Mac hmac256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac256.init(secretKey);
            byte[] bytes = hmac256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * bytes.length);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}
