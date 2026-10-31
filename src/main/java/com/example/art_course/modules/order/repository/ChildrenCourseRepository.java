package com.example.art_course.modules.order.repository;

import com.example.art_course.modules.order.entity.ChildrenCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChildrenCourseRepository extends JpaRepository<ChildrenCourse, UUID> {
    List<ChildrenCourse> findByOrderId(UUID orderId);
    List<ChildrenCourse> findByOrderIdIn(java.util.Collection<UUID> orderIds);
    List<ChildrenCourse> findByOrderIdAndCourseId(UUID orderId, UUID courseId);
    boolean existsByOrderIdAndCourseIdAndChildId(UUID orderId, UUID courseId, UUID childId);
}
