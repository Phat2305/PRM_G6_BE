
        package com.example.art_course.offer.repository;

import com.example.art_course.offer.entity.Offer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findAllByRequestId(UUID requestId);

    List<Offer> findAllByTeacherId(UUID teacherId);

    List<Offer> findAllByStatus(String status);

    List<Offer> findAllByRequestIdAndTeacherId(
            UUID requestId,
            UUID teacherId
    );
}
