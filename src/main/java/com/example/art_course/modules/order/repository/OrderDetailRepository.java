package com.example.art_course.modules.order.repository;

import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.modules.order.entity.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, UUID> {
    List<OrderDetail> findByOrderId(UUID orderId);
    List<OrderDetail> findByOrderIdAndSupplyIdIsNotNull(UUID orderId);
    List<OrderDetail> findByOrderIdAndCustomizeRequestIdIsNotNull(UUID orderId);
    List<OrderDetail> findByCustomizeRequestId(UUID customizeRequestId);
    List<OrderDetail> findByDeliveryStatus(DeliveryStatus deliveryStatus);
    List<OrderDetail> findByDeliveryStatusAndDeliveredAtBefore(DeliveryStatus deliveryStatus, Instant threshold);
}
