package com.example.art_course.modules.refund.repository;

import com.example.art_course.modules.refund.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository("be2RefundRepository")
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByOrderId(UUID orderId);
    List<Refund> findByParentIdOrderByCreatedAtDesc(UUID parentId);
    List<Refund> findByStatus(String status);

    // ADM-04: tổng tiền đã hoàn xong.
    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM Refund r WHERE UPPER(r.status) = 'COMPLETED'")
    BigDecimal sumCompleted();
}
