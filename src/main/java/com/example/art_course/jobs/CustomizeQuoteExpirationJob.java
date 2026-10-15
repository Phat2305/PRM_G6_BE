package com.example.art_course.jobs;

import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomizeQuoteExpirationJob {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final JobExecutionTracker jobTracker;

    @Scheduled(fixedDelay = 300000) // Quét mỗi 5 phút
    @SchedulerLock(name = "CustomizeQuoteExpirationJob", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    public void run() {
        jobTracker.track("CustomizeQuoteExpirationJob", this::expireOverdueCustomizeQuotes);
    }

    @Transactional
    public int expireOverdueCustomizeQuotes() {
        Instant deadline = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING_PAYMENT);
        int count = 0;

        for (Order order : pendingOrders) {
            if (order.getCreatedAt().isBefore(deadline)) {
                List<OrderDetail> details = orderDetailRepository.findByOrderIdAndProductType(order.getId(), "CUSTOMIZE");
                if (!details.isEmpty()) {
                    order.setStatus(OrderStatus.EXPIRED);
                    orderRepository.save(order);
                    count++;
                    log.info("[JOB-CUSTOMIZE-EXPIRE] Đã hủy đơn báo giá customize quá hạn 24h: {}", order.getId());
                }
            }
        }
        return count;
    }
}
