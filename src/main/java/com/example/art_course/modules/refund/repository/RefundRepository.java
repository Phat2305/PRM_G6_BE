package com.example.art_course.modules.refund.repository;

import com.example.art_course.modules.refund.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository("be2RefundRepository")
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByOrderId(UUID orderId);
    List<Refund> findByParentIdOrderByCreatedAtDesc(UUID parentId);
    List<Refund> findByStatus(String status);

    // FIN-08: Staff duyệt danh sách hoàn tiền theo trang.
    Page<Refund> findByStatus(String status, Pageable pageable);

    // Khóa dòng khi duyệt/từ chối/hoàn tất để hai Staff cùng xử lý một yêu cầu thì chạy lần lượt.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Be2Refund r WHERE r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") UUID id);

    // ADM-04: tổng tiền đã hoàn xong.
    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM Be2Refund r WHERE UPPER(r.status) = 'COMPLETED'")
    BigDecimal sumCompleted();

    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM Be2Refund r "
            + "WHERE r.paymentId = :paymentId AND UPPER(r.status) = 'COMPLETED'")
    BigDecimal sumCompletedByPaymentId(@Param("paymentId") UUID paymentId);

    // FIN-09: phần giáo viên chịu = tiền hoàn trừ hoa hồng của các dòng được hoàn.
    @Query(value = """
            SELECT COALESCE(SUM(r.total_amount - COALESCE((
                SELECT SUM(d.commission_amount)
                FROM refund_items i JOIN order_details d ON d.id = i.order_detail_id
                WHERE i.refund_id = r.id
            ), 0)), 0)
            FROM refunds r WHERE r.payment_id = :paymentId AND UPPER(r.status) = 'COMPLETED'
            """, nativeQuery = true)
    BigDecimal sumCompletedTeacherAmount(@Param("paymentId") UUID paymentId);
}
