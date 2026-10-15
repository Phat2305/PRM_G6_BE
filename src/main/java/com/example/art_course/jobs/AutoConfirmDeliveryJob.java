package com.example.art_course.jobs;

import com.example.art_course.modules.fulfillment.service.FulfillmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoConfirmDeliveryJob {

    private final FulfillmentService fulfillmentService;
    private final JobExecutionTracker jobTracker;

    @Scheduled(cron = "${app.jobs.auto-confirm-cron:0 0 3 * * *}")
    @SchedulerLock(name = "AutoConfirmDeliveryJob", lockAtLeastFor = "1m", lockAtMostFor = "15m")
    public void run() {
        jobTracker.track("AutoConfirmDeliveryJob", fulfillmentService::autoConfirmDeliveredItems);
    }
}
