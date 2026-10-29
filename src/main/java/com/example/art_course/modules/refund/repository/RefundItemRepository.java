package com.example.art_course.modules.refund.repository;

import com.example.art_course.modules.refund.entity.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefundItemRepository extends JpaRepository<RefundItem, UUID> {
    List<RefundItem> findByRefundId(UUID refundId);
    List<RefundItem> findByOrderDetailId(UUID orderDetailId);

    // FIN-07: một dòng chỉ có một yêu cầu chưa bị từ chối.
    @Query("SELECT COUNT(i) > 0 FROM RefundItem i, Be2Refund r WHERE i.refundId = r.id "
            + "AND i.orderDetailId = :detailId AND UPPER(r.status) <> 'REJECTED'")
    boolean hasNonRejectedRefund(@Param("detailId") UUID detailId);
}
