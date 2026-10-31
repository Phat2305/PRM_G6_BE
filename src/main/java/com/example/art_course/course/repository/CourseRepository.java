
package com.example.art_course.course.repository;

import com.example.art_course.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findAllByTeacherId(UUID teacherId);
    java.util.Optional<Course> findByCoverImageUrl(String coverImageUrl);
    // Khóa học riêng đã làm cho một yêu cầu customize (CUS-11)
    java.util.Optional<Course> findFirstByCustomizeRequestId(UUID customizeRequestId);

    List<Course> findAllByStatus(String status);

    // Các khóa (trong danh sách) đã có người mua: dòng đơn còn hiệu lực (đã trả hoặc đang chờ trả, chưa hoàn tiền) hoặc
    // bé đang có quyền học. Khóa như vậy không xóa và không sửa nội dung được; đơn đang chờ trả cũng tính để khách
    // không trả tiền cho khóa vừa bị xóa hoặc vừa bị đổi nội dung.
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT d.course_id FROM order_details d JOIN orders o ON o.id = d.order_id
            WHERE d.course_id IN (:courseIds) AND o.status IN ('PAID', 'PENDING_PAYMENT')
              AND d.delivery_status IS DISTINCT FROM 'REFUNDED'
            UNION
            SELECT e.course_id FROM child_enroll e WHERE e.course_id IN (:courseIds)
            """, nativeQuery = true)
    List<UUID> findCourseIdsWithBuyers(
            @org.springframework.data.repository.query.Param("courseIds") java.util.Collection<UUID> courseIds);

    default boolean hasBuyers(UUID courseId) {
        return !findCourseIdsWithBuyers(List.of(courseId)).isEmpty();
    }

    List<Course> findAllByCustomizeRequestIdIn(java.util.Collection<UUID> customizeRequestIds);

    List<Course> findByStatusIgnoreCase(String status);

    // Khóa dòng khi Staff duyệt/từ chối để hai Staff cùng xử lý một khóa thì chạy lần lượt.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Course c WHERE c.id = :id")
    java.util.Optional<Course> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<Course> findAllByTeacherIdAndStatus(
            UUID teacherId,
            String status
    );
}
