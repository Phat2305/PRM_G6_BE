
        package com.example.art_course.customize_request.repository;

import com.example.art_course.customize_request.entity.CustomizeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomizeRequestRepository
        extends JpaRepository<CustomizeRequest, UUID> {

    List<CustomizeRequest> findAllByParentId(UUID parentId);

    List<CustomizeRequest> findAllByStatus(String status);

    // Khóa dòng khi Staff duyệt/từ chối để hai Staff cùng xử lý một bài thì chạy lần lượt.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT r FROM CustomizeRequest r WHERE r.id = :id")
    java.util.Optional<CustomizeRequest> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<CustomizeRequest> findAllByCategoryIgnoreCase(
            String category
    );
}
