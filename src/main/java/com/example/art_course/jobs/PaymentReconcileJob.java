package com.example.art_course.jobs;

import com.example.art_course.modules.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileJob {

    private final PaymentService paymentService;
    private final JobExecutionTracker jobTracker;

    @Scheduled(cron = "${app.jobs.reconcile-cron:0 0 2 * * *}")
    @SchedulerLock(name = "PaymentReconcileJob", lockAtLeastFor = "1m", lockAtMostFor = "15m")
    public void run() {
        jobTracker.track("PaymentReconcileJob", this::reconcileAll);
    }

    // Mỗi thanh toán chạy trong transaction riêng của PaymentService.reconcilePayment: một cái lỗi thì rollback cái đó, các cái còn lại vẫn chạy.
    int reconcileAll() {
        log.info("[RECONCILE] Bắt đầu đối soát giao dịch cổng thanh toán...");
        int reconciled = 0;
        for (UUID paymentId : paymentService.findReconcilablePaymentIds()) {
            try {
                if (paymentService.reconcilePayment(paymentId)) {
                    reconciled++;
                }
            } catch (Exception ex) {
                log.warn("[RECONCILE] Lỗi đối soát cho payment {}, đã rollback: {}", paymentId, ex.getMessage());
            }
        }
        log.info("[RECONCILE] Kết thúc đối soát. Số giao dịch được đồng bộ: {}", reconciled);
        return reconciled;
    }
}
