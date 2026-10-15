package com.example.art_course.jobs;

import com.example.art_course.jobs.entity.ScheduledJobLog;
import com.example.art_course.jobs.repository.ScheduledJobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.IntSupplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutionTracker {

    private final ScheduledJobLogRepository jobLogRepository;

    public void track(String jobName, IntSupplier action) {
        ScheduledJobLog jobLog = ScheduledJobLog.builder()
                .jobName(jobName)
                .status("RUNNING")
                .startedAt(Instant.now())
                .build();
        ScheduledJobLog savedLog = jobLogRepository.save(jobLog);

        try {
            int recordsAffected = action.getAsInt();
            savedLog.setStatus("SUCCESS");
            savedLog.setRecordsAffected(recordsAffected);
            savedLog.setFinishedAt(Instant.now());
            jobLogRepository.save(savedLog);
            log.info("[JOB-TRACKER] Job {} hoàn tất thành công ({} bản ghi bị ảnh hưởng)", jobName, recordsAffected);
        } catch (Exception ex) {
            savedLog.setStatus("FAILED");
            savedLog.setErrorMessage(ex.getMessage());
            savedLog.setFinishedAt(Instant.now());
            jobLogRepository.save(savedLog);
            log.error("[JOB-TRACKER] Job {} thất bại: {}", jobName, ex.getMessage(), ex);
        }
    }
}
