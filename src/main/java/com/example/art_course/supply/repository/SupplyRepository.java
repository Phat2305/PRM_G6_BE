package com.example.art_course.supply.repository;

import com.example.art_course.supply.entity.Supply;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplyRepository
        extends JpaRepository<Supply, UUID> {

    List<Supply> findAllByTeacherId(UUID teacherId);

    List<Supply> findAllByStatus(String status);

    List<Supply> findAllByTeacherIdAndStatus(
            UUID teacherId,
            String status
    );

    List<Supply> findByStatusIgnoreCase(String status);

    List<Supply> findByStatusInIgnoreCase(Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Supply s WHERE s.id = :id")
    Optional<Supply> findByIdForUpdate(@Param("id") UUID id);
}
