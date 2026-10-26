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

    // MSG-05: tin nhắn mới cho bên còn lại. Còn thông báo tin mới chưa đọc của hội thoại này thì không tạo thêm.
    @Async
    public void notifyNewMessage(UUID recipientId, UUID conversationId, String preview) {
        if (notificationRepository.existsByRecipientIdAndTypeAndReferenceIdAndIsReadFalse(
                recipientId, "NEW_MESSAGE", conversationId)) {
            return;
        }
        sendNotificationAsync(recipientId, "Tin nhắn mới", preview, "NEW_MESSAGE", conversationId);
    }

    // CUS-05: giáo viên gửi báo giá; tham chiếu là yêu cầu customize.
    // Chạy luồng riêng: lỗi ghi thông báo không được làm rollback báo giá.
    @Async
    public void notifyNewOffer(UUID requestId, UUID parentId, BigDecimal price, Integer deliveryDays) {
        String days = deliveryDays == null ? "" : ", bàn giao trong " + deliveryDays + " ngày";
        sendNotificationAsync(parentId, "Báo giá mới",
                "Giáo viên gửi báo giá " + vnd(price) + days + ".", "NEW_OFFER", requestId);
    }

    // CUS-08: phụ huynh chấp nhận báo giá; giáo viên chờ phụ huynh thanh toán.
    // Chạy luồng riêng: lỗi ghi thông báo không được làm rollback báo giá.
    @Async
    public void notifyOfferAccepted(UUID requestId, UUID teacherId, BigDecimal price) {
        sendNotificationAsync(teacherId, "Báo giá được chấp nhận",
                "Phụ huynh đã chấp nhận báo giá " + vnd(price) + ", đang chờ thanh toán.", "OFFER_ACCEPTED", requestId);
    }

    // CUS-11: giáo viên bàn giao khóa học riêng; phụ huynh vào xem và gán cho bé.
    @Async
    public void notifyCustomizeDelivered(UUID requestId, UUID parentId, String courseName) {
        sendNotificationAsync(parentId, "Khóa học riêng đã bàn giao",
                "Giáo viên đã bàn giao \"" + courseName + "\". Mở yêu cầu để xem và gán cho bé.",
                "CUSTOMIZE_DELIVERED", requestId);
    }

    // CUS-12: phụ huynh xác nhận hoàn thành; tiền ký quỹ được xét giải ngân.
    @Async
    public void notifyCustomizeCompleted(UUID requestId, UUID teacherId, String courseName) {
        sendNotificationAsync(teacherId, "Phụ huynh đã nhận khóa học",
                "Phụ huynh đã xác nhận hoàn thành \"" + courseName + "\".",
                "CUSTOMIZE_COMPLETED", requestId);
    }

    private static String vnd(BigDecimal amount) {
        return String.format("%,d", amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue())
                .replace(',', '.') + "đ";
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
