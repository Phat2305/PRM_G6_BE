package com.example.art_course.jobs;

import com.example.art_course.modules.finance.service.EscrowReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoReleaseEscrowJob {

    private final EscrowReleaseService escrowReleaseService;
    private final JobExecutionTracker jobTracker;

    @Scheduled(cron = "${app.jobs.auto-release-escrow-cron:0 0 4 * * *}")
    @SchedulerLock(name = "AutoReleaseEscrowJob", lockAtLeastFor = "1m", lockAtMostFor = "15m")
    public void run() {
        jobTracker.track("AutoReleaseEscrowJob", () -> {
            int released = 0;
            for (var orderId : escrowReleaseService.findHeldOrderIds()) {
                try {
                    if (escrowReleaseService.tryReleaseEscrow(orderId)) released++;
                } catch (Exception e) {
                    log.warn("[ESCROW] Đơn {} giải ngân lỗi, transaction đã rollback: {}", orderId, e.getMessage());
                }
            }
            return released;
        });
    }
}
