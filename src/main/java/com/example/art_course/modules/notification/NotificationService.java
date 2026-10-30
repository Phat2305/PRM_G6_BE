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

    // CUS-18: đơn thanh toán báo giá customize hết hạn; yêu cầu mở lại để báo giá/chấp nhận lại.
    @Async
    public void notifyCustomizeQuoteExpired(UUID requestId, UUID recipientId) {
        sendNotificationAsync(recipientId, "Báo giá customize hết hạn thanh toán",
                "Đơn thanh toán đã quá hạn, yêu cầu được mở lại để chọn báo giá.", "CUSTOMIZE_QUOTE_EXPIRED", requestId);
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
    // Staff duyệt khóa học thường: báo giáo viên khóa đã công khai.
    public void notifyCourseApproved(UUID courseId, UUID teacherId, String courseName) {
        sendNotificationAsync(teacherId, "Khóa học đã được duyệt",
                "Khóa \"" + courseName + "\" đã được Staff duyệt và công khai.", "COURSE_APPROVED", courseId);
    }

    // Staff từ chối khóa: kèm lý do và số lần được gửi lại còn lại.
    public void notifyCourseRejected(UUID courseId, UUID teacherId, String courseName, String reason, int remaining) {
        String tail = remaining > 0
                ? "Bạn còn " + remaining + " lần gửi lại."
                : "Khóa đã bị từ chối 3 lần nên không gửi lại được nữa.";
        sendNotificationAsync(teacherId, "Khóa học bị từ chối",
                "Khóa \"" + courseName + "\" bị từ chối: " + reason + ". " + tail, "COURSE_REJECTED", courseId);
    }

    // Staff gỡ khóa đang công khai: báo giáo viên lý do; người đã mua vẫn học được.
    public void notifyCourseHidden(UUID courseId, UUID teacherId, String courseName, String reason) {
        sendNotificationAsync(teacherId, "Khóa học bị gỡ khỏi danh mục",
                "Khóa \"" + courseName + "\" đã bị gỡ: " + reason + ". Bạn sửa lại rồi gửi duyệt để đăng lại.",
                "COURSE_HIDDEN", courseId);
    }

    // Staff duyệt bài đăng: bài đã lên bảng yêu cầu cho giáo viên báo giá.
    public void notifyRequestApproved(UUID requestId, UUID parentId, String title) {
        sendNotificationAsync(parentId, "Bài đăng đã được duyệt",
                "Bài đăng \"" + title + "\" đã được duyệt và hiện cho giáo viên báo giá.", "REQUEST_APPROVED", requestId);
    }

    public void notifyRequestRejected(UUID requestId, UUID parentId, String title, String reason, int remaining) {
        String tail = remaining > 0
                ? "Bạn sửa lại và gửi lại được " + remaining + " lần nữa."
                : "Bài đăng đã bị từ chối 3 lần nên không gửi lại được nữa.";
        sendNotificationAsync(parentId, "Bài đăng bị từ chối",
                "Bài đăng \"" + title + "\" bị từ chối: " + reason + ". " + tail, "REQUEST_REJECTED", requestId);
    }

    // Phụ huynh chưa đồng ý với bản bàn giao: báo giáo viên kèm lý do (lý do cũng được gửi vào cuộc trò chuyện).
    public void notifyCustomizeDeliveryRejected(UUID requestId, UUID teacherId, String courseName, String reason) {
        sendNotificationAsync(teacherId, "Phụ huynh chưa đồng ý với bản bàn giao",
                "Phụ huynh chưa đồng ý với \"" + courseName + "\": " + reason + ". Hãy sửa rồi bàn giao lại.",
                "CUSTOMIZE_DELIVERY_REJECTED", requestId);
    }

    // Khóa riêng bị Staff từ chối đủ số lần: yêu cầu bị hủy, phụ huynh có thể xin hoàn tiền.
    public void notifyCustomizeFailed(UUID requestId, UUID parentId, String courseName) {
        sendNotificationAsync(parentId, "Khóa học riêng không bàn giao được",
                "Khóa \"" + courseName + "\" không qua được kiểm duyệt. Bạn có thể gửi yêu cầu hoàn tiền cho đơn này.",
                "CUSTOMIZE_FAILED", requestId);
    }

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
                "Đã chuyển khoản thành công " + amount + " VND"
                        + (transactionCode == null ? ". Bằng chứng có trong lịch sử rút tiền." : " (Mã GD: " + transactionCode + ")"), "WITHDRAWAL_PAID", requestId);
    }

    public void notifyRefundCompleted(UUID refundId, UUID parentId, BigDecimal amount, String transactionCode) {
        sendNotificationAsync(parentId, "Hoàn tiền thành công",
                "Khoản tiền hoàn " + amount + " VND đã được chuyển khoản"
                        + (transactionCode == null ? ". Bằng chứng có trong lịch sử hoàn tiền." : " (Mã GD: " + transactionCode + ")"), "REFUND_COMPLETED", refundId);
    }

    public void notifyRefundRejected(UUID refundId, UUID parentId, BigDecimal amount, String reason) {
        sendNotificationAsync(parentId, "Yêu cầu hoàn tiền bị từ chối",
                "Yêu cầu hoàn " + amount + " VND bị từ chối: " + reason, "REFUND_REJECTED", refundId);
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
