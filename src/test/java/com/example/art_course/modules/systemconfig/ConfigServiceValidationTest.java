package com.example.art_course.modules.systemconfig;

import com.example.art_course.common.AppError;
import com.example.art_course.modules.systemconfig.entity.SystemConfig;
import com.example.art_course.modules.systemconfig.repository.SystemConfigRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Cấu hình hệ thống: giá trị sai không được lưu vì code thanh toán đọc thẳng các giá trị này")
class ConfigServiceValidationTest {

    private final SystemConfigRepository repo = mock(SystemConfigRepository.class);
    private final ConfigService service = new ConfigService(repo);
    private final UUID admin = UUID.randomUUID();

    private String saved(String key, String value) {
        clearInvocations(repo);
        when(repo.findByKey(key)).thenReturn(Optional.empty());
        when(repo.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        service.updateConfig(key, value, admin);
        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        return captor.getValue().getValue();
    }

    private void assertRejected(String key, String value) {
        clearInvocations(repo);
        AppError error = assertThrows(AppError.class, () -> service.updateConfig(key, value, admin),
                key + "=" + value);
        assertEquals(400, error.getStatus().value());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("hoa hồng phải là số từ 0 đến 1; chữ, số âm, trên 100% đều bị từ chối")
    void commissionRate() {
        assertEquals("0.25", saved("commission_rate_course", "0.25"));
        assertEquals("0", saved("commission_rate_supply", "0"));
        assertRejected("commission_rate_course", "abc");
        assertRejected("commission_rate_course", "-0.1");
        assertRejected("commission_rate_course", "20");
        assertRejected("commission_rate_supply", "1.01");
        assertRejected("commission_rate_supply", null);
    }

    @Test
    @DisplayName("mốc thời gian là số nguyên trong khoảng cho phép")
    void wholeNumbers() {
        assertEquals("15", saved("order_payment_timeout_minutes", " 15 "));
        assertEquals("15", saved("order_payment_timeout_minutes", "15.0"));
        assertRejected("order_payment_timeout_minutes", "0");
        assertRejected("order_payment_timeout_minutes", "15.5");
        assertRejected("auto_confirm_delivery_days", "9999");
        assertRejected("quote_payment_timeout_hours", "");
    }

    @Test
    @DisplayName("mốc N và X cho phép để trống để tắt tự động")
    void optionalDays() {
        assertEquals("", saved("auto_release_course_days", ""));
        assertEquals("", saved("customize_auto_confirm_days", null));
        assertEquals("30", saved("auto_release_course_days", "30"));
        assertRejected("customize_auto_confirm_days", "-3");
    }

    @Test
    @DisplayName("phí vận chuyển không âm; khóa lạ bị từ chối thay vì tạo dòng cấu hình rác")
    void shippingFeeAndUnknownKeys() {
        assertEquals("0", saved("default_shipping_fee", "0"));
        assertRejected("default_shipping_fee", "-1");
        assertRejected("not_a_real_key", "1");
    }
}
