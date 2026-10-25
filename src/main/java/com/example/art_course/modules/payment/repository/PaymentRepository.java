package com.example.art_course.modules.payment.repository;

import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.modules.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    // Chuyển trạng thái nguyên tử: chỉ một người thắng khi webhook, hỏi trạng thái và job đối soát cùng xử lý một giao dịch.
    @Transactional
    @Modifying
    @Query("UPDATE Payment p SET p.status = :to WHERE p.id = :id AND p.status = :from")
    int transition(@Param("id") UUID id, @Param("from") PaymentStatus from, @Param("to") PaymentStatus to);

    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByGatewayOrderCode(Long gatewayOrderCode);
    Optional<Payment> findByGatewayAndGatewayTransactionId(String gateway, String gatewayTransactionId);
    List<Payment> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);
    List<Payment> findByStatus(PaymentStatus status);

    // ADM-04: tổng tiền theo trạng thái, mỗi dòng là [status, tổng amount, tổng netAmount, tổng commissionAmount].
    @Query("SELECT p.status, SUM(p.amount), SUM(p.netAmount), SUM(p.commissionAmount) "
            + "FROM Payment p WHERE p.status IN :statuses GROUP BY p.status")
    List<Object[]> sumsByStatus(@Param("statuses") Collection<PaymentStatus> statuses);
}
