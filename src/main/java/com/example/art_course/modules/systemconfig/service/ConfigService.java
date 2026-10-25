package com.example.art_course.modules.systemconfig.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.Money;
import com.example.art_course.modules.systemconfig.entity.SystemConfig;
import com.example.art_course.modules.systemconfig.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SystemConfigRepository systemConfigRepository;

    @Transactional(readOnly = true)
    public Money getDefaultShippingFee() {
        return systemConfigRepository.findByKey("shipping_fee_default")
                .or(() -> systemConfigRepository.findByKey("default_shipping_fee"))
                .map(sc -> Money.of(new BigDecimal(sc.getValue())))
                .orElse(Money.of(30000));
    }

    @Transactional(readOnly = true)
    public BigDecimal getCommissionRateCourse() {
        return systemConfigRepository.findByKey("commission_rate_course")
                .map(sc -> new BigDecimal(sc.getValue()))
                .orElse(new BigDecimal("0.20"));
    }

    @Transactional(readOnly = true)
    public BigDecimal getCommissionRateSupply() {
        return systemConfigRepository.findByKey("commission_rate_supply")
                .map(sc -> new BigDecimal(sc.getValue()))
                .orElse(new BigDecimal("0.10"));
    }

    @Transactional(readOnly = true)
    public int getOrderPaymentTimeoutMinutes() {
        return systemConfigRepository.findByKey("order_payment_timeout_minutes")
                .map(sc -> Integer.parseInt(sc.getValue()))
                .orElse(15);
    }

    // #20: hạn thanh toán sau khi chấp nhận báo giá customize, mặc định 24 giờ
    @Transactional(readOnly = true)
    public int getQuotePaymentTimeoutHours() {
        return systemConfigRepository.findByKey("quote_payment_timeout_hours")
                .map(sc -> Integer.parseInt(sc.getValue()))
                .orElse(24);
    }

    @Transactional(readOnly = true)
    public int getAutoConfirmDeliveryDays() {
        return systemConfigRepository.findByKey("auto_confirm_delivery_days")
                .map(sc -> Integer.parseInt(sc.getValue()))
                .orElse(7);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Integer> getAutoReleaseCourseDays() {
        return systemConfigRepository.findByKey("auto_release_course_days")
                .map(SystemConfig::getValue)
                .filter(val -> val != null && !val.isBlank())
                .map(Integer::parseInt);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Integer> getCustomizeAutoConfirmDays() {
        return systemConfigRepository.findByKey("customize_auto_confirm_days")
                .map(SystemConfig::getValue)
                .filter(val -> val != null && !val.isBlank())
                .map(Integer::parseInt);
    }

    // Giá trị hợp lệ theo từng khóa cấu hình: các khóa này được code khác đọc bằng BigDecimal/Integer.parseInt,
    // một giá trị sai (chữ, số âm, hoa hồng > 100%) sẽ làm hỏng thanh toán hoặc tính hoa hồng sai.
    // optional = cho phép để trống (tắt tính năng tự động).
    private record Rule(boolean decimal, long min, long max, boolean optional) {}

    private static final java.util.Map<String, Rule> RULES = java.util.Map.of(
            "commission_rate_course", new Rule(true, 0, 1, false),
            "commission_rate_supply", new Rule(true, 0, 1, false),
            "order_payment_timeout_minutes", new Rule(false, 1, 1440, false),
            "quote_payment_timeout_hours", new Rule(false, 1, 720, false),
            "auto_confirm_delivery_days", new Rule(false, 1, 90, false),
            "auto_release_course_days", new Rule(false, 1, 365, true),
            "customize_auto_confirm_days", new Rule(false, 1, 365, true),
            "default_shipping_fee", new Rule(false, 0, 1_000_000, false),
            "shipping_fee_default", new Rule(false, 0, 1_000_000, false));

    @Transactional
    public SystemConfig updateConfig(String key, String value, UUID adminId) {
        String clean = validate(key, value);
        value = clean;
        SystemConfig config = systemConfigRepository.findByKey(key)
                .orElseGet(() -> SystemConfig.builder().key(key).build());
        config.setValue(value);
        config.setUpdatedAt(Instant.now());
        config.setUpdatedBy(adminId);
        return systemConfigRepository.save(config);
    }

    private static String validate(String key, String value) {
        Rule rule = RULES.get(key);
        if (rule == null) {
            throw new AppError(HttpStatus.BAD_REQUEST, "UNKNOWN_CONFIG_KEY", "Khóa cấu hình không tồn tại: " + key);
        }
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            if (rule.optional()) {
                return "";
            }
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_CONFIG_VALUE", "Giá trị của " + key + " không được để trống");
        }
        BigDecimal number;
        try {
            number = new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_CONFIG_VALUE", "Giá trị của " + key + " phải là số");
        }
        if (!rule.decimal() && number.stripTrailingZeros().scale() > 0) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_CONFIG_VALUE", "Giá trị của " + key + " phải là số nguyên");
        }
        if (number.compareTo(BigDecimal.valueOf(rule.min())) < 0 || number.compareTo(BigDecimal.valueOf(rule.max())) > 0) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_CONFIG_VALUE",
                    "Giá trị của " + key + " phải từ " + rule.min() + " đến " + rule.max());
        }
        // Lưu dạng chuẩn để Integer.parseInt/BigDecimal đọc lại được (vd " 15 " hoặc "15.0")
        return rule.decimal() ? number.stripTrailingZeros().toPlainString() : number.toBigInteger().toString();
    }

    @Transactional(readOnly = true)
    public java.util.List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findAll();
    }
}
