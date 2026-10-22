package com.example.art_course.modules.payment.repository;

import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.modules.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByGatewayOrderCode(Long gatewayOrderCode);
    Optional<Payment> findByGatewayAndGatewayTransactionId(String gateway, String gatewayTransactionId);
    List<Payment> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);
    List<Payment> findByStatus(PaymentStatus status);
}
