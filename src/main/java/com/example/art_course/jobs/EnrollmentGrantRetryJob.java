package com.example.art_course.jobs;

import com.example.art_course.modules.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// PAY-06: các dòng đã trả tiền nhưng thiếu ghi danh được thử lại, không mất khi restart.
@Component
@RequiredArgsConstructor
@Slf4j
public class EnrollmentGrantRetryJob {
    private final PaymentService paymentService;
    private final JobExecutionTracker jobTracker;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "EnrollmentGrantRetryJob", lockAtLeastFor = "10s", lockAtMostFor = "5m")
    public void run() {
        jobTracker.track("EnrollmentGrantRetryJob", () -> {
            int repaired = 0;
            for (var id : paymentService.findMissingEnrollmentIds()) {
                try {
                    if (paymentService.repairEnrollment(id)) repaired++;
                } catch (Exception e) {
                    log.warn("[ENROLLMENT-RETRY] Dòng {} chưa cấp được quyền, sẽ thử lại: {}", id, e.getMessage());
                }
            }
            return repaired;
        });
    }
}
