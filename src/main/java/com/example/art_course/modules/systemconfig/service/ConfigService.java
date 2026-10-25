package com.example.art_course.modules.systemconfig.service;

import com.example.art_course.common.Money;
import com.example.art_course.modules.systemconfig.entity.SystemConfig;
import com.example.art_course.modules.systemconfig.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public SystemConfig updateConfig(String key, String value, UUID adminId) {
        SystemConfig config = systemConfigRepository.findByKey(key)
                .orElseGet(() -> SystemConfig.builder().key(key).build());
        config.setValue(value);
        config.setUpdatedAt(Instant.now());
        config.setUpdatedBy(adminId);
        return systemConfigRepository.save(config);
    }

    @Transactional(readOnly = true)
    public java.util.List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findAll();
    }
}
