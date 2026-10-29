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

import java.time.Instant;
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

    // Giao dịch đã thanh toán từ mốc `since` (paid_at, hoặc created_at nếu chưa ghi paid_at) cho biểu đồ doanh thu.
    @Query("SELECT p FROM Payment p WHERE p.teacherId = :teacherId AND p.status IN :statuses "
            + "AND COALESCE(p.paidAt, p.createdAt) >= :since")
    List<Payment> findPaidByTeacherSince(@Param("teacherId") UUID teacherId,
                                         @Param("statuses") Collection<PaymentStatus> statuses,
                                         @Param("since") Instant since);

    @Query("SELECT p FROM Payment p WHERE p.status IN :statuses AND COALESCE(p.paidAt, p.createdAt) >= :since")
    List<Payment> findPaidSince(@Param("statuses") Collection<PaymentStatus> statuses,
                                @Param("since") Instant since);

    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByGatewayOrderCode(Long gatewayOrderCode);

    @Query("SELECT p.id FROM Payment p WHERE p.gatewayOrderCode = :code")
    Optional<UUID> findIdByGatewayOrderCode(@Param("code") Long code);

    @Query("SELECT p.orderId FROM Payment p WHERE p.id = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    @Query("SELECT p.id FROM Payment p WHERE p.gateway = :gateway AND p.gatewayTransactionId = :tx")
    Optional<UUID> findIdByGatewayAndGatewayTransactionId(@Param("gateway") String gateway, @Param("tx") String tx);
    Optional<Payment> findByGatewayAndGatewayTransactionId(String gateway, String gatewayTransactionId);
    List<Payment> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);
    List<Payment> findByStatus(PaymentStatus status);

    // ADM-04: tổng tiền theo trạng thái, mỗi dòng là [status, tổng amount, tổng netAmount, tổng commissionAmount].
    @Query("SELECT p.status, SUM(p.amount), SUM(p.netAmount), SUM(p.commissionAmount) "
            + "FROM Payment p WHERE p.status IN :statuses GROUP BY p.status")
    List<Object[]> sumsByStatus(@Param("statuses") Collection<PaymentStatus> statuses);
}
