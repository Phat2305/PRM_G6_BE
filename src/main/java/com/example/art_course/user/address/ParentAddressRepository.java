package com.example.art_course.user.address;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ParentAddressRepository extends JpaRepository<ParentAddress, UUID> {

    List<ParentAddress> findAllByParentIdOrderByIsDefaultDescCreatedAtDesc(UUID parentId);

    long countByParentId(UUID parentId);

    // Bỏ cờ mặc định của mọi địa chỉ khác để index duy nhất (parent_id) WHERE is_default không bị vi phạm.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ParentAddress a set a.isDefault = false where a.parentId = :parentId and a.isDefault = true")
    void clearDefault(@Param("parentId") UUID parentId);
}
