package com.example.art_course.customize_request.repository;

import com.example.art_course.customize_request.entity.CustomizeDeliveryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomizeDeliveryHistoryRepository extends JpaRepository<CustomizeDeliveryHistory, UUID> {
    boolean existsByRequestId(UUID requestId);
    Optional<CustomizeDeliveryHistory> findFirstByRequestIdAndResolvedAtIsNullOrderByDeliveredAtDesc(UUID requestId);
    List<CustomizeDeliveryHistory> findAllByRequestIdOrderByDeliveredAtDesc(UUID requestId);
}
