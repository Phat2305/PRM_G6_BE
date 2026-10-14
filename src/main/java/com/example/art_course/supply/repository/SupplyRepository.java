
package com.example.art_course.supply.repository;

import com.example.art_course.supply.entity.Supply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplyRepository
        extends JpaRepository<Supply, UUID> {

    List<Supply> findAllByTeacherId(UUID teacherId);

    List<Supply> findAllByStatus(String status);

    List<Supply> findAllByTeacherIdAndStatus(
            UUID teacherId,
            String status
    );
}
