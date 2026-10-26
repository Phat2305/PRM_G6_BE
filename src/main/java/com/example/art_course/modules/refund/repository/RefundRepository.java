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
}
