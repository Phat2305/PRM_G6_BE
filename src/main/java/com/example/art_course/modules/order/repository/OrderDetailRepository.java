package com.example.art_course.modules.order.repository;

import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.modules.order.entity.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, UUID> {

    @Query("SELECT d.id FROM OrderDetail d WHERE d.deliveryStatus = :status AND d.deliveredAt < :before ORDER BY d.orderId, d.id")
    List<UUID> findDeliveredIdsBefore(@Param("status") DeliveryStatus status, @Param("before") Instant before);
    @Query("SELECT d.orderId FROM OrderDetail d WHERE d.id = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    // PAY-06: đơn đã trả nhưng chưa có dòng ghi danh; không kích hoạt lại ghi danh bị Admin khóa.
    @Query(value = """
            SELECT d.id FROM order_details d
            JOIN orders o ON o.id = d.order_id
            JOIN payments p ON p.order_id = o.id
            LEFT JOIN child_enroll e ON e.child_id = d.child_id AND e.course_id = d.course_id
            WHERE o.status = 'PAID' AND p.status IN ('HELD', 'RELEASED')
              AND d.course_id IS NOT NULL AND d.child_id IS NOT NULL
              AND (d.delivery_status IS NULL OR d.delivery_status <> 'REFUNDED') AND e.child_id IS NULL
            """, nativeQuery = true)
    List<UUID> findMissingEnrollmentIds();

    List<OrderDetail> findByOrderId(UUID orderId);
    List<OrderDetail> findByOrderIdIn(java.util.Collection<UUID> orderIds);
    List<OrderDetail> findByOrderIdAndSupplyIdIsNotNull(UUID orderId);
    List<OrderDetail> findByOrderIdAndCustomizeRequestIdIsNotNull(UUID orderId);
    List<OrderDetail> findByCustomizeRequestId(UUID customizeRequestId);
    List<OrderDetail> findByDeliveryStatus(DeliveryStatus deliveryStatus);
    List<OrderDetail> findByDeliveryStatusAndDeliveredAtBefore(DeliveryStatus deliveryStatus, Instant threshold);
}
