package com.example.art_course.jobs;

import com.example.art_course.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationJob {

    private final OrderService orderService;
    private final JobExecutionTracker jobTracker;

    @Scheduled(fixedDelay = 60000) // Quét mỗi 60 giây
    @SchedulerLock(name = "OrderExpirationJob", lockAtLeastFor = "10s", lockAtMostFor = "5m")
    public void run() {
        jobTracker.track("OrderExpirationJob", orderService::expirePendingOrders);
    }
}
