package com.example.art_course.modules.systemconfig.service;

import com.example.art_course.common.Money;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.modules.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Báo giáo viên khi phí ship mặc định của sàn đổi. Chỉ báo giáo viên chưa đặt phí riêng (họ mới bị ảnh hưởng), và chỉ
 * khi mức thực tế đổi. Gọi sau khi cấu hình đã lưu xong để thông báo không đi trước một giao dịch có thể bị hủy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingFeeNotifier {

    private final TeacherNames teacherNames;
    private final NotificationService notificationService;

    public int notifyIfChanged(Money before, Money after) {
        if (before.compareTo(after) == 0) {
            return 0;
        }
        long oldFee = before.getAmount().longValue();
        long newFee = after.getAmount().longValue();
        var teachers = teacherNames.teacherIdsOnDefaultShippingFee();
        for (UUID teacherId : teachers) {
            notificationService.notifyShippingFeeChanged(teacherId, oldFee, newFee);
        }
        log.info("[CONFIG] Phí ship mặc định đổi {} -> {}, đã báo {} giáo viên", oldFee, newFee, teachers.size());
        return teachers.size();
    }
}
