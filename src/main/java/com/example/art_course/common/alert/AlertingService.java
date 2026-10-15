package com.example.art_course.common.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Dịch vụ cảnh báo và log tập trung (NFR-16).
 * Kích hoạt cảnh báo khi phát hiện: lỗi chữ ký webhook, tỷ lệ lỗi API tăng, đơn hàng bất thường.
 */
@Slf4j
@Service
public class AlertingService {

    public void alertWebhookFailure(String gateway, String reason, Map<String, String> payload) {
        log.error("[ALERT-NFR16][WEBHOOK-FAILURE] Cảnh báo lỗi cổng thanh toán {}: {}. Payload: {} at {}",
                gateway, reason, payload, Instant.now());
    }

    public void alertSystemAnomaly(String category, String description) {
        log.error("[ALERT-NFR16][ANOMALY] Cảnh báo bất thường hệ thống [{}]: {} at {}",
                category, description, Instant.now());
    }
}
