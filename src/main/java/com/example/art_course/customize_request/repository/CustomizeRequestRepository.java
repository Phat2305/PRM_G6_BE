
        package com.example.art_course.customize_request.repository;

import com.example.art_course.customize_request.entity.CustomizeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomizeRequestRepository
        extends JpaRepository<CustomizeRequest, UUID> {

    interface ContactCount {
        UUID getRequestId();
        long getContacted();
    }

    // CUS-04/15: a teacher counts once across conversations and all their quote revisions.
    @Query(value = """
            SELECT request_id AS "requestId", COUNT(DISTINCT teacher_id) AS contacted
            FROM (
                SELECT request_id, teacher_id FROM offers WHERE request_id IN (:requestIds)
                UNION ALL
                SELECT context_id AS request_id, teacher_id FROM conversations
                WHERE UPPER(TRIM(context_type)) = 'CUSTOMIZE_REQUEST' AND context_id IN (:requestIds)
            ) contacts GROUP BY request_id
            """, nativeQuery = true)
    List<ContactCount> countTeacherContacts(@Param("requestIds") Collection<UUID> requestIds);

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
