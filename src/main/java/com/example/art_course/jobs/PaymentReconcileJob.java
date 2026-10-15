package com.example.art_course.jobs;

import com.example.art_course.modules.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileJob {

    private final PaymentService paymentService;
    private final JobExecutionTracker jobTracker;

    @Scheduled(cron = "${app.jobs.reconcile-cron:0 0 2 * * *}")
    @SchedulerLock(name = "PaymentReconcileJob", lockAtLeastFor = "1m", lockAtMostFor = "15m")
    public void run() {
        jobTracker.track("PaymentReconcileJob", paymentService::reconcileDailyPayments);
    }
}
