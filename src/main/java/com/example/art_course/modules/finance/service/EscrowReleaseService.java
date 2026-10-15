package com.example.art_course.modules.finance.service;

import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.contracts.events.CourseCompletedEvent;
import com.example.art_course.contracts.events.CourseReviewedEvent;
import com.example.art_course.modules.course.repository.CourseReviewRepository;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowReleaseService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final RefundRepository refundRepository;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final ConfigService configService;

    // Bộ nhớ tạm lưu các khóa học đã được BE1 báo hoàn thành bởi trẻ (childId + courseId)
    private final ConcurrentHashMap<String, Boolean> completedCoursesMap = new ConcurrentHashMap<>();

    /**
     * Kiểm tra và giải phóng tiền ký quỹ cho một đơn hàng (Mục 7.1 & #26).
     * Chỉ giải phóng khi MỌI dòng trong đơn đủ điều kiện.
     */
    @Transactional
    public boolean tryReleaseEscrow(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return false;

        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.HELD) {
            return false;
        }

        // FIN-10: Khoản đang có hoàn tiền hoặc khiếu nại thì KHÔNG giải phóng
        List<Refund> activeRefunds = refundRepository.findByOrderId(orderId);
        boolean hasPendingRefund = activeRefunds.stream()
                .anyMatch(r -> "PENDING".equalsIgnoreCase(r.getStatus()) || "APPROVED".equalsIgnoreCase(r.getStatus()));
        if (hasPendingRefund) {
            log.info("[ESCROW] Đơn hàng {} đang có yêu cầu hoàn tiền chưa giải quyết, từ chối giải phóng", orderId);
            return false;
        }

        List<OrderDetail> details = orderDetailRepository.findByOrderId(orderId);
        if (details.isEmpty()) return false;

        // Kiểm tra MỌI dòng chi tiết trong đơn
        for (OrderDetail detail : details) {
            if (!isDetailEligibleForRelease(order, detail)) {
                log.info("[ESCROW] Đơn hàng {}: Dòng sản phẩm {} ({}) chưa đủ điều kiện giải phóng (#26)",
                        orderId, detail.getId(), detail.getProductName());
                return false;
            }
        }

        // TẤT CẢ các dòng đều đã đủ điều kiện -> Giải phóng HELD -> RELEASED trong 1 @Transactional
        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedAt(Instant.now());
        paymentRepository.save(payment);

        ledgerService.recordEntry(
                payment.getTeacherId(),
                "RELEASE",
                payment.getNetAmount(),
                payment.getId(),
                null,
                null,
                "Giải phóng tiền ký quỹ cho đơn hàng " + order.getId(),
                null
        );

        notificationService.notifyEscrowReleased(payment.getId(), payment.getTeacherId(), payment.getNetAmount());
        log.info("[ESCROW-SUCCESS] Đã giải phóng thành công {} VND cho Teacher {} từ đơn hàng {}",
                payment.getNetAmount(), payment.getTeacherId(), orderId);

        return true;
    }

    private boolean isDetailEligibleForRelease(Order order, OrderDetail detail) {
        String type = detail.getProductType();

        if ("COURSE".equalsIgnoreCase(type)) {
            // 1. Phụ huynh đã gửi đánh giá (SHOP-09)
            if (detail.getCourseId() != null &&
                    courseReviewRepository.findByCourseIdAndParentId(detail.getCourseId(), order.getParentId()).isPresent()) {
                return true;
            }

            // 2. Trẻ đã hoàn thành khóa học (CourseCompletedEvent)
            if (detail.getChildId() != null && detail.getCourseId() != null) {
                String key = detail.getChildId() + ":" + detail.getCourseId();
                if (Boolean.TRUE.equals(completedCoursesMap.get(key))) {
                    return true;
                }
            }

            // 3. Mốc N ngày tự giải phóng nếu Admin bật (system_config: auto_release_course_days)
            Optional<Integer> autoReleaseDays = configService.getAutoReleaseCourseDays();
            if (autoReleaseDays.isPresent() && autoReleaseDays.get() > 0) {
                Instant deadline = order.getCreatedAt().plus(autoReleaseDays.get(), ChronoUnit.DAYS);
                if (Instant.now().isAfter(deadline)) {
                    return true;
                }
            }

            return false;
        }

        if ("SUPPLY".equalsIgnoreCase(type)) {
            // Dụng cụ: Phụ huynh xác nhận hoặc job tự động xác nhận sau M ngày -> COMPLETED
            return detail.getDeliveryStatus() == DeliveryStatus.COMPLETED;
        }

        if ("CUSTOMIZE".equalsIgnoreCase(type)) {
            Optional<Integer> autoConfirmDays = configService.getCustomizeAutoConfirmDays();
            if (autoConfirmDays.isPresent() && autoConfirmDays.get() > 0) {
                Instant deadline = order.getCreatedAt().plus(autoConfirmDays.get(), ChronoUnit.DAYS);
                return Instant.now().isAfter(deadline);
            }
            return detail.getDeliveryStatus() == DeliveryStatus.COMPLETED;
        }

        return false;
    }

    /**
     * Kích hoạt tự động khi Parent đánh giá khóa học (SHOP-09).
     */
    @EventListener
    public void onCourseReviewed(CourseReviewedEvent event) {
        log.info("[ESCROW-EVENT] Nhận sự kiện phụ huynh {} đánh giá khóa học {}", event.getParentId(), event.getCourseId());
        List<Order> parentOrders = orderRepository.findByParentIdOrderByCreatedAtDesc(event.getParentId());
        for (Order order : parentOrders) {
            tryReleaseEscrow(order.getId());
        }
    }

    /**
     * Kích hoạt khi Child hoàn thành khóa học (LRN-07 / BE1).
     */
    @EventListener
    public void onCourseCompleted(CourseCompletedEvent event) {
        log.info("[ESCROW-EVENT] Nhận sự kiện trẻ {} hoàn thành khóa học {}", event.getChildId(), event.getCourseId());
        String key = event.getChildId() + ":" + event.getCourseId();
        completedCoursesMap.put(key, true);

        // Quét và giải phóng các đơn hàng liên quan
        List<Payment> heldPayments = paymentRepository.findByStatus(PaymentStatus.HELD);
        for (Payment payment : heldPayments) {
            tryReleaseEscrow(payment.getOrderId());
        }
    }

    /**
     * Quét và giải phóng các khoản escrow đủ điều kiện.
     */
    public int autoReleaseEscrow() {
        log.info("[ESCROW] Bắt đầu quét tự động giải phóng tiền ký quỹ...");
        List<Payment> heldPayments = paymentRepository.findByStatus(PaymentStatus.HELD);
        int count = 0;
        for (Payment payment : heldPayments) {
            if (tryReleaseEscrow(payment.getOrderId())) {
                count++;
            }
        }
        log.info("[ESCROW] Kết thúc quét. Số đơn hàng được giải phóng: {}", count);
        return count;
    }
}
