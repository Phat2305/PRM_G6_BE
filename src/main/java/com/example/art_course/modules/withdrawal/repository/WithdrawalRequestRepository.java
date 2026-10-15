package com.example.art_course.modules.withdrawal.repository;

import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.modules.withdrawal.entity.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {
    List<WithdrawalRequest> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);
    boolean existsByTeacherIdAndStatusIn(UUID teacherId, Collection<WithdrawalStatus> statuses);
    Optional<WithdrawalRequest> findFirstByTeacherIdAndStatusIn(UUID teacherId, Collection<WithdrawalStatus> statuses);
    List<WithdrawalRequest> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status);
}
