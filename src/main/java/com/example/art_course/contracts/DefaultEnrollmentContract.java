package com.example.art_course.contracts;

import com.example.art_course.enrollment.entity.Enrollment;
import com.example.art_course.enrollment.entity.EnrollmentId;
import com.example.art_course.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultEnrollmentContract implements IEnrollmentContract {

    private final EnrollmentRepository enrollmentRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void grantAccess(UUID childId, UUID courseId, UUID orderId) {
        log.info("[Enrollment Contract] Cấp quyền học cho childId: {}, courseId: {}, orderId: {}", childId, courseId, orderId);
        if (childId != null && courseId != null) {
            Enrollment enrollment = Enrollment.builder()
                    .id(new EnrollmentId(childId, courseId))
                    .status("active")
                    .build();
            enrollmentRepository.saveAndFlush(enrollment);
            log.info("[Enrollment Contract] Đã lưu quyền học vào child_enroll thành công!");
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void revokeAccess(UUID childId, UUID courseId, UUID refundId) {
        log.info("[Enrollment Contract] Thu hồi quyền học cho childId: {}, courseId: {}, refundId: {}", childId, courseId, refundId);
        if (childId != null && courseId != null) {
            enrollmentRepository.deleteById(new EnrollmentId(childId, courseId));
        }
    }

    @Override
    public boolean isEnrolled(UUID childId, UUID courseId) {
        if (childId == null || courseId == null) return false;
        return enrollmentRepository.existsByIdChildIdAndIdCourseIdAndStatusIgnoreCase(childId, courseId, "active");
    }
}
