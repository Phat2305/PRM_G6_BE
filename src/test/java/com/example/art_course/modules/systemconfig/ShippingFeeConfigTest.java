package com.example.art_course.modules.systemconfig;

import com.example.art_course.modules.systemconfig.entity.SystemConfig;
import com.example.art_course.modules.systemconfig.repository.SystemConfigRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Phí ship mặc định: khóa default_shipping_fee là khóa màn Cấu hình của Admin ghi nên phải thắng khóa cũ shipping_fee_default.
class ShippingFeeConfigTest {

    private final SystemConfigRepository repo = mock(SystemConfigRepository.class);
    private final ConfigService service = new ConfigService(repo);

    private static SystemConfig row(String key, String value) {
        return SystemConfig.builder().key(key).value(value).build();
    }

    @Test
    void adminValueWinsOverTheLegacyKey() {
        when(repo.findByKey("default_shipping_fee")).thenReturn(Optional.of(row("default_shipping_fee", "20000")));
        when(repo.findByKey("shipping_fee_default")).thenReturn(Optional.of(row("shipping_fee_default", "30000")));

        assertEquals(0, service.getDefaultShippingFee().getAmount().compareTo(new java.math.BigDecimal("20000")));
    }

    @Test
    void legacyKeyIsUsedOnlyWhenTheNewOneIsMissing() {
        when(repo.findByKey("default_shipping_fee")).thenReturn(Optional.empty());
        when(repo.findByKey("shipping_fee_default")).thenReturn(Optional.of(row("shipping_fee_default", "25000")));

        assertEquals(0, service.getDefaultShippingFee().getAmount().compareTo(new java.math.BigDecimal("25000")));
    }

    @Test
    void fallsBackTo30000WhenNothingIsConfigured() {
        when(repo.findByKey("default_shipping_fee")).thenReturn(Optional.empty());
        when(repo.findByKey("shipping_fee_default")).thenReturn(Optional.empty());

        assertEquals(0, service.getDefaultShippingFee().getAmount().compareTo(new java.math.BigDecimal("30000")));
    }
}
