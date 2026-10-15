package com.example.art_course.modules.notification;

import com.example.art_course.modules.notification.entity.Notification;
import com.example.art_course.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Async
    public void sendNotificationAsync(UUID recipientId, String title, String content, String type, UUID referenceId) {
        try {
            Notification notification = Notification.builder()
                    .recipientId(recipientId)
                    .title(title)
                    .content(content)
                    .type(type)
                    .referenceId(referenceId)
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
            log.info("[NOTIFICATION-SENT] Gửi thông báo Push/Email tới User {}: [{}] {}", recipientId, type, title);
        } catch (Exception ex) {
            log.error("[NOTIFICATION-ERROR] Lỗi gửi thông báo tới {}: {}", recipientId, ex.getMessage());
        }
    }

    public void notifyOrderPaid(UUID orderId, UUID parentId, UUID teacherId) {
        sendNotificationAsync(parentId, "Thanh toán thành công",
                "Đơn hàng " + orderId + " đã được thanh toán thành công", "ORDER_PAID", orderId);
        sendNotificationAsync(teacherId, "Có đơn hàng mới",
                "Bạn có đơn hàng mới " + orderId + " vừa được thanh toán", "ORDER_RECEIVED", orderId);
    }

    public void notifyDeliveryStatusChanged(UUID orderDetailId, UUID parentId, UUID teacherId, String newStatus) {
        sendNotificationAsync(parentId, "Cập nhật vận chuyển",
                "Trạng thái giao hàng mục " + orderDetailId + " đã đổi thành " + newStatus, "DELIVERY_UPDATE", orderDetailId);
    }

    public void notifyWithdrawalApproved(UUID requestId, UUID teacherId, BigDecimal amount) {
        sendNotificationAsync(teacherId, "Yêu cầu rút tiền được duyệt",
                "Yêu cầu rút " + amount + " VND đã được duyệt và đang chờ giải ngân", "WITHDRAWAL_APPROVED", requestId);
    }

    public void notifyWithdrawalRejected(UUID requestId, UUID teacherId, BigDecimal amount, String reason) {
        sendNotificationAsync(teacherId, "Yêu cầu rút tiền bị từ chối",
                "Yêu cầu rút " + amount + " VND bị từ chối: " + reason, "WITHDRAWAL_REJECTED", requestId);
    }

    public void notifyWithdrawalPaid(UUID requestId, UUID teacherId, BigDecimal amount, String transactionCode) {
        sendNotificationAsync(teacherId, "Chi trả rút tiền thành công",
                "Đã chuyển khoản thành công " + amount + " VND (Mã GD: " + transactionCode + ")", "WITHDRAWAL_PAID", requestId);
    }

    public void notifyRefundCompleted(UUID refundId, UUID parentId, BigDecimal amount, String transactionCode) {
        sendNotificationAsync(parentId, "Hoàn tiền thành công",
                "Khoản tiền hoàn " + amount + " VND đã được chuyển khoản (Mã GD: " + transactionCode + ")", "REFUND_COMPLETED", refundId);
    }

    public void notifyEscrowReleased(UUID paymentId, UUID teacherId, BigDecimal amount) {
        sendNotificationAsync(teacherId, "Tiền ký quỹ đã được giải phóng",
                "Khoản tiền " + amount + " VND đã được giải phóng vào số dư có thể rút của bạn", "ESCROW_RELEASED", paymentId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getMyNotifications(UUID recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID recipientId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipientId().equals(recipientId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public int markAllAsRead(UUID recipientId) {
        return notificationRepository.markAllAsRead(recipientId);
    }
}
