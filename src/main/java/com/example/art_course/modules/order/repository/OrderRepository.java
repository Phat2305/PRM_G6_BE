package com.example.art_course.modules.order.repository;

import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.modules.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByParentIdOrderByCreatedAtDesc(UUID parentId);
    List<Order> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByStatusAndExpiredAtLessThanEqual(OrderStatus status, Instant now);
}
