
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
