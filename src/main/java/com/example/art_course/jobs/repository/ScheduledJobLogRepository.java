package com.example.art_course.jobs.repository;

import com.example.art_course.jobs.entity.ScheduledJobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledJobLogRepository extends JpaRepository<ScheduledJobLog, UUID> {
    List<ScheduledJobLog> findByJobNameOrderByStartedAtDesc(String jobName);
}
