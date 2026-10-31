package com.example.art_course.modules.systemconfig;

import com.example.art_course.common.Money;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.modules.finance.controller.AdminFinanceController;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.systemconfig.entity.SystemConfig;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.modules.systemconfig.service.ShippingFeeNotifier;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// Admin đổi phí ship mặc định: báo giáo viên đang dùng mức mặc định, không báo khi giá trị không đổi hay khi đổi cấu hình khác.
class ShippingFeeNotifierTest {

    private final TeacherNames teacherNames = mock(TeacherNames.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ShippingFeeNotifier notifier = new ShippingFeeNotifier(teacherNames, notifications);
    private final UUID t1 = UUID.randomUUID();
    private final UUID t2 = UUID.randomUUID();

    @Test
    void notifiesEveryTeacherOnTheDefaultWithOldAndNewFee() {
        when(teacherNames.teacherIdsOnDefaultShippingFee()).thenReturn(List.of(t1, t2));

        int sent = notifier.notifyIfChanged(Money.of(30000), Money.of(25000));

        assertEquals(2, sent);
        verify(notifications).notifyShippingFeeChanged(t1, 30000L, 25000L);
        verify(notifications).notifyShippingFeeChanged(t2, 30000L, 25000L);
    }

    @Test
    void unchangedFeeSendsNothingAndDoesNotEvenLookUpTeachers() {
        assertEquals(0, notifier.notifyIfChanged(Money.of(30000), Money.of(30000)));

        verifyNoInteractions(teacherNames, notifications);
    }

    @Test
    void messageHasTheFeesAndNamesNoOneWhoChangedIt() {
        var repo = mock(com.example.art_course.modules.notification.repository.NotificationRepository.class);
        var real = new NotificationService(repo);

        real.notifyShippingFeeChanged(t1, 30000L, 25000L);

        verify(repo).save(org.mockito.ArgumentMatchers.argThat(n ->
                "SHIPPING_FEE_CHANGED".equals(n.getType())
                        && n.getRecipientId().equals(t1)
                        && n.getContent().contains("30.000đ")
                        && n.getContent().contains("25.000đ")
                        && n.getContent().contains("đơn đã tạo giữ nguyên")
                        && !n.getContent().toLowerCase().contains("admin")));
    }

    // Controller chỉ báo khi khóa là phí ship và mức thực tế đổi; khóa cấu hình khác không đụng tới giáo viên.
    @Test
    void controllerNotifiesOnlyForShippingFeeKeys() {
        var configService = mock(ConfigService.class);
        var shipping = mock(ShippingFeeNotifier.class);
        var controller = new AdminFinanceController(mock(WithdrawalService.class), configService,
                mock(TeacherNames.class), shipping);
        var auth = new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null);
        when(configService.getDefaultShippingFee()).thenReturn(Money.of(30000), Money.of(25000));
        when(configService.updateConfig(any(), any(), any())).thenReturn(SystemConfig.builder().key("k").value("v").build());

        controller.updateConfig("default_shipping_fee", Map.of("value", "25000"), auth);
        verify(shipping).notifyIfChanged(Money.of(30000), Money.of(25000));

        controller.updateConfig("commission_rate_course", Map.of("value", "0.2"), auth);
        verifyNoMoreInteractions(shipping);
    }
}
