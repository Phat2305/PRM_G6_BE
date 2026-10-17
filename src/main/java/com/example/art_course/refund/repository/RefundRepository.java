
        package com.example.art_course.refund.repository;

import com.example.art_course.refund.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findAllByParentId(UUID parentId);

    List<Refund> findAllByPaymentId(UUID paymentId);

    List<Refund> findAllByStatus(String status);
}
