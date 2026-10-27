package com.example.art_course.jobs;

import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.offer.entity.Offer;
import com.example.art_course.offer.repository.OfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// CUS-18: đơn thanh toán báo giá customize quá hạn -> EXPIRED, yêu cầu ACCEPTED trở lại OPEN và báo giá về pending,
// đúng như khi phụ huynh thu hồi báo giá (OfferService.revoke).
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomizeQuoteExpirationJob {

    private static final long FALLBACK_HOURS = 24;

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CustomizeRequestRepository requestRepository;
    private final OfferRepository offerRepository;
    private final NotificationService notificationService;
    private final JobExecutionTracker jobTracker;

    @Scheduled(fixedDelay = 300000) // Quét mỗi 5 phút
    @SchedulerLock(name = "CustomizeQuoteExpirationJob", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    public void run() {
        jobTracker.track("CustomizeQuoteExpirationJob", this::expireOverdueCustomizeQuotes);
    }

    @Transactional
    public int expireOverdueCustomizeQuotes() {
        Instant now = Instant.now();
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING_PAYMENT);
        int count = 0;

        for (Order order : pendingOrders) {
            // Hạn theo cấu hình lúc tạo đơn (expiredAt); đơn cũ chưa có thì mặc định 24h kể từ lúc tạo.
            Instant deadline = order.getExpiredAt() != null
                    ? order.getExpiredAt()
                    : order.getCreatedAt().plus(FALLBACK_HOURS, ChronoUnit.HOURS);
            if (deadline.isAfter(now)) {
                continue;
            }
            List<OrderDetail> details = orderDetailRepository.findByOrderIdAndCustomizeRequestIdIsNotNull(order.getId());
            if (details.isEmpty()) {
                continue;
            }
            order.setStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);
            for (OrderDetail detail : details) {
                reopenRequest(detail.getCustomizeRequestId(), order);
            }
            count++;
            log.info("[JOB-CUSTOMIZE-EXPIRE] Đã hủy đơn báo giá customize quá hạn: {}", order.getId());
        }
        return count;
    }

    private void reopenRequest(UUID requestId, Order order) {
        CustomizeRequest request = requestRepository.findById(requestId).orElse(null);
        // Chỉ mở lại khi yêu cầu còn chờ thanh toán; đã FUNDED/hủy thì giữ nguyên.
        if (request == null || !"ACCEPTED".equalsIgnoreCase(request.getStatus())) {
            return;
        }
        request.setStatus("OPEN");
        requestRepository.save(request);
        for (Offer offer : offerRepository.findAllByRequestId(requestId)) {
            if ("accepted".equalsIgnoreCase(offer.getStatus()) || "rejected".equalsIgnoreCase(offer.getStatus())) {
                offer.setStatus("pending");
                offerRepository.save(offer);
            }
        }
        notificationService.notifyCustomizeQuoteExpired(requestId, order.getParentId());
        notificationService.notifyCustomizeQuoteExpired(requestId, order.getTeacherId());
    }
}
