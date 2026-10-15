package com.example.art_course.modules.refund.repository;

import com.example.art_course.modules.refund.entity.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefundItemRepository extends JpaRepository<RefundItem, UUID> {
    List<RefundItem> findByRefundId(UUID refundId);
    List<RefundItem> findByOrderDetailId(UUID orderDetailId);
}
