
        package com.example.art_course.customize_request.repository;

import com.example.art_course.customize_request.entity.CustomizeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomizeRequestRepository
        extends JpaRepository<CustomizeRequest, UUID> {

    List<CustomizeRequest> findAllByParentId(UUID parentId);

    List<CustomizeRequest> findAllByStatus(String status);

    List<CustomizeRequest> findAllByCategoryIgnoreCase(
            String category
    );
}
