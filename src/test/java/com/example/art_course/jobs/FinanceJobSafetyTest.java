package com.example.art_course.jobs;

import com.example.art_course.jobs.repository.ScheduledJobLogRepository;
import com.example.art_course.modules.finance.service.EscrowReleaseService;
import com.example.art_course.modules.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinanceJobSafetyTest {
    private final ScheduledJobLogRepository logs = mock(ScheduledJobLogRepository.class);
    private final JobExecutionTracker tracker = new JobExecutionTracker(logs);

    @Test
    void escrowFailureDoesNotPreventNextOrderFromRunning() {
        when(logs.save(any())).thenAnswer(i -> i.getArgument(0));
        EscrowReleaseService escrow = mock(EscrowReleaseService.class);
        UUID failed = UUID.randomUUID(), next = UUID.randomUUID();
        when(escrow.findHeldOrderIds()).thenReturn(List.of(failed, next));
        when(escrow.tryReleaseEscrow(failed)).thenThrow(new IllegalStateException("ledger unavailable"));
        when(escrow.tryReleaseEscrow(next)).thenReturn(true);
        new AutoReleaseEscrowJob(escrow, tracker).run();
        verify(escrow).tryReleaseEscrow(next);
        verify(logs, atLeastOnce()).save(argThat(log -> "SUCCESS".equals(log.getStatus()) && log.getRecordsAffected() == 1));
    }

    @Test
    void failedEnrollmentRemainsDiscoverableOnTheNextRun() {
        when(logs.save(any())).thenAnswer(i -> i.getArgument(0));
        PaymentService payments = mock(PaymentService.class);
        UUID missing = UUID.randomUUID();
        when(payments.findMissingEnrollmentIds()).thenReturn(List.of(missing));
        when(payments.repairEnrollment(missing)).thenThrow(new IllegalStateException("unavailable")).thenReturn(true);
        EnrollmentGrantRetryJob job = new EnrollmentGrantRetryJob(payments, tracker);
        job.run();
        job.run();
        verify(payments, times(2)).findMissingEnrollmentIds();
        verify(payments, times(2)).repairEnrollment(missing);
    }
}
