package com.example.art_course.modules.order.repository;

import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.modules.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    // PAY-08, FIN-07: mọi thay đổi thanh toán/hoàn tiền/tồn kho khóa đơn trước.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    List<Order> findByParentIdOrderByCreatedAtDesc(UUID parentId);
    List<Order> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);
    List<Order> findByStatus(OrderStatus status);
    long countByStatus(OrderStatus status);
    List<Order> findByStatusAndExpiredAtLessThanEqual(OrderStatus status, Instant now);

    @Query("SELECT o.id FROM Order o WHERE o.status = :status ORDER BY o.id")
    List<UUID> findIdsByStatus(@Param("status") OrderStatus status);

    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.expiredAt <= :now ORDER BY o.id")
    List<UUID> findExpiredIds(@Param("status") OrderStatus status, @Param("now") Instant now);
}
