package com.example.art_course.modules.payment.gateway;

import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.payment.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class VNPayPaymentGateway implements PaymentGateway {

    public static final String GATEWAY_CODE = "VNPAY";

    @Value("${app.payment.vnpay.tmn-code:6MOOS6DN}")
    private String tmnCode;

    @Value("${app.payment.vnpay.hash-secret:KYOLQZTMXXHKMWUXXANCABHMUCCAWGIX}")
    private String hashSecret;

    @Value("${app.payment.vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpayUrl;

    @Override
    public String getGatewayCode() {
        return GATEWAY_CODE;
    }

    @Override
    public String createPaymentUrl(Order order, Payment payment, String returnUrl) {
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_TxnRef = payment.getId() != null ? payment.getId().toString() : order.getId().toString();
        String vnp_IpAddr = "127.0.0.1";
        String vnp_TmnCode = tmnCode != null && !tmnCode.isBlank() ? tmnCode : "6MOOS6DN";

        long amountInVNDTimes100 = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amountInVNDTimes100));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getId());
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", returnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String value = vnp_Params.get(fieldName);
            if (value != null && !value.isEmpty()) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII)).append('&');
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII)).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII)).append('&');
            }
        }

        if (hashData.length() > 0) hashData.setLength(hashData.length() - 1);
        if (query.length() > 0) query.setLength(query.length() - 1);

        String secret = hashSecret != null && !hashSecret.isBlank() ? hashSecret : "KYOLQZTMXXHKMWUXXANCABHMUCCAWGIX";
        String vnp_SecureHash = hmacSHA512(secret, hashData.toString());
        return vnpayUrl + "?" + query.toString() + "&vnp_SecureHash=" + vnp_SecureHash;
    }

    @Override
    public GatewayCallbackResult verifyCallback(Map<String, String> params) {
        String secureHash = params.get("vnp_SecureHash");
        if (secureHash == null) {
            return new GatewayCallbackResult(false, false, null, BigDecimal.ZERO, "Thiếu chữ ký vnp_SecureHash");
        }

        List<String> fieldNames = new ArrayList<>();
        for (String key : params.keySet()) {
            if (key.startsWith("vnp_") && !"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key)) {
                fieldNames.add(key);
            }
        }
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder rawHashData = new StringBuilder();
        for (String fieldName : fieldNames) {
            String value = params.get(fieldName);
            if (value != null && !value.isEmpty()) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII)).append('&');
                rawHashData.append(fieldName).append('=').append(value).append('&');
            }
        }
        if (hashData.length() > 0) hashData.setLength(hashData.length() - 1);
        if (rawHashData.length() > 0) rawHashData.setLength(rawHashData.length() - 1);

        String secret = hashSecret != null && !hashSecret.isBlank() ? hashSecret : "KYOLQZTMXXHKMWUXXANCABHMUCCAWGIX";
        String calculatedHash = hmacSHA512(secret, hashData.toString());
        String rawCalculatedHash = hmacSHA512(secret, rawHashData.toString());

        boolean validSignature = secureHash.equalsIgnoreCase(calculatedHash) || secureHash.equalsIgnoreCase(rawCalculatedHash);
        String responseCode = params.get("vnp_ResponseCode");
        boolean success = "00".equals(responseCode);

        String txId = params.get("vnp_TransactionNo");
        String amountStr = params.get("vnp_Amount");
        BigDecimal amount = BigDecimal.ZERO;
        if (amountStr != null) {
            try {
                amount = new BigDecimal(amountStr).divide(BigDecimal.valueOf(100));
            } catch (Exception ignored) {}
        }

        return new GatewayCallbackResult(validSignature, success, txId, amount, "VNPay response code: " + responseCode);
    }

    @Override
    public GatewayQueryResult queryTransaction(Payment payment) {
        boolean isPaid = payment.getGatewayTransactionId() != null;
        return new GatewayQueryResult(true, isPaid, payment.getGatewayTransactionId(), payment.getAmount());
    }

    public static String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
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
