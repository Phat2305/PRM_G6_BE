package com.example.art_course.modules.finance.service;

import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.common.statemachine.OrderStatus;
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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowReleaseService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final com.example.art_course.modules.order.repository.ChildrenCourseRepository childrenCourseRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final RefundRepository refundRepository;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final ConfigService configService;

    private final com.example.art_course.child_progress.repository.ChildProgressRepository progressRepository;
    private final com.example.art_course.lesson.repository.LessonRepository lessonRepository;
    private final com.example.art_course.customize_request.repository.CustomizeRequestRepository customizeRequests;

    /**
     * Kiểm tra và giải phóng tiền ký quỹ cho một đơn hàng (Mục 7.1 & #26).
     * Chỉ giải phóng khi MỌI dòng trong đơn đủ điều kiện.
     */
    @Transactional
    public boolean tryReleaseEscrow(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PAID) return false;

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

        // FIN-09: chỉ giải ngân phần còn lại sau các khoản đã hoàn từ HELD.
        var amount = payment.getNetAmount().subtract(refundRepository.sumCompletedTeacherAmount(payment.getId()))
                .max(java.math.BigDecimal.ZERO);
        if (paymentRepository.transition(payment.getId(), PaymentStatus.HELD, PaymentStatus.RELEASED) == 0) return false;
        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedAt(Instant.now());
        paymentRepository.save(payment);

        ledgerService.recordEntry(
                payment.getTeacherId(),
                "RELEASE",
                amount,
                payment.getId(),
                null,
                null,
                "Giải phóng tiền ký quỹ cho đơn hàng " + order.getId(),
                null
        );

        notificationService.notifyEscrowReleased(payment.getId(), payment.getTeacherId(), amount);
        log.info("[ESCROW-SUCCESS] Đã giải phóng thành công {} VND cho Teacher {} từ đơn hàng {}",
                payment.getNetAmount(), payment.getTeacherId(), orderId);

        return true;
    }

    private boolean isDetailEligibleForRelease(Order order, OrderDetail detail) {
        if (detail.getDeliveryStatus() == DeliveryStatus.REFUNDED) return true;
        String type = detail.getProductType();

        if ("COURSE".equalsIgnoreCase(type)) {
            // 1. Phụ huynh đã gửi đánh giá (SHOP-09)
            if (detail.getCourseId() != null &&
                    courseReviewRepository.findByCourseIdAndParentId(detail.getCourseId(), order.getParentId()).isPresent()) {
                return true;
            }

            // 2. Tiến độ đã lưu trong DB vẫn đúng sau khi khởi động lại.
            if (detail.getCourseId() != null) {
                var lessonIds = lessonRepository.findAllByCourseId(detail.getCourseId()).stream()
                        .map(com.example.art_course.lesson.entity.Lesson::getId).toList();
                var childIds = childrenCourseRepository.findByOrderIdAndCourseId(order.getId(), detail.getCourseId())
                        .stream().map(com.example.art_course.modules.order.entity.ChildrenCourse::getChildId).toList();
                if (childIds.isEmpty() && detail.getChildId() != null) childIds = List.of(detail.getChildId());
                if (!lessonIds.isEmpty() && !childIds.isEmpty() && childIds.stream().allMatch(childId ->
                        progressRepository.findAllByIdChildId(childId).stream()
                                .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                                .map(p -> p.getId().getLessonId()).collect(java.util.stream.Collectors.toSet())
                                .containsAll(lessonIds))) {
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
            // CUS-12: phụ huynh xác nhận hoàn thành là đủ; mốc X ngày (nếu Admin bật) là đường tự giải ngân thêm.
            if (detail.getDeliveryStatus() == DeliveryStatus.COMPLETED) {
                return true;
            }
            Optional<Integer> autoConfirmDays = configService.getCustomizeAutoConfirmDays();
            if (autoConfirmDays.isPresent() && autoConfirmDays.get() > 0
                    && detail.getDeliveredAt() != null
                    && customizeRequests.findById(detail.getCustomizeRequestId())
                            .map(r -> "DELIVERED".equalsIgnoreCase(r.getStatus())).orElse(false)) {
                Instant deadline = detail.getDeliveredAt().plus(autoConfirmDays.get(), ChronoUnit.DAYS);
                return Instant.now().isAfter(deadline);
            }
            return false;
        }

        return false;
    }

    /**
     * Kích hoạt tự động khi Parent đánh giá khóa học (SHOP-09).
     */
    @EventListener
    @Transactional
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
    @Transactional
    public void onCourseCompleted(CourseCompletedEvent event) {
        log.info("[ESCROW-EVENT] Nhận sự kiện trẻ {} hoàn thành khóa học {}", event.getChildId(), event.getCourseId());

        // Quét và giải phóng các đơn hàng liên quan
        List<Payment> heldPayments = paymentRepository.findByStatus(PaymentStatus.HELD);
        for (Payment payment : heldPayments) {
            tryReleaseEscrow(payment.getOrderId());
        }
    }

    // FIN-03: job gọi từng đơn qua bean để mỗi lần giải ngân có transaction riêng.
    @Transactional(readOnly = true)
    public List<UUID> findHeldOrderIds() {
        return paymentRepository.findByStatus(PaymentStatus.HELD).stream().map(Payment::getOrderId).toList();
    }
}
