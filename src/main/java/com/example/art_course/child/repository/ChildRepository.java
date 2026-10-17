
package com.example.art_course.child.repository;

import com.example.art_course.child.entity.Child;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChildRepository extends JpaRepository<Child, UUID> {

    List<Child> findAllByParentId(UUID parentId);

    Optional<Child> findByUserId(UUID userId);

    Optional<Child> findByUserIdAndParentId(
            UUID userId,
            UUID parentId
    );
}
