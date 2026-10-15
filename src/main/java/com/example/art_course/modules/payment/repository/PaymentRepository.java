package com.example.art_course.modules.payment.repository;

import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.modules.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByGatewayAndGatewayTransactionId(String gateway, String gatewayTransactionId);
    List<Payment> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);
    List<Payment> findByStatus(PaymentStatus status);
}
