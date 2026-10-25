package com.example.art_course.enrollment.service;

import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.enrollment.dto.EnrollmentResponse;
import com.example.art_course.enrollment.dto.UpdateEnrollmentStatusRequest;
import com.example.art_course.enrollment.entity.Enrollment;
import com.example.art_course.enrollment.repository.EnrollmentRepository;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final ChildRepository childRepository;
    private final CourseRepository courseRepository;

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("active", "inactive", "cancelled");

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            ChildRepository childRepository,
            CourseRepository courseRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.childRepository = childRepository;
        this.courseRepository = courseRepository;
    }

    public EnrollmentResponse getById(
            UUID callerId,
            UUID childId,
            UUID courseId) {

        requireSelfOrParent(callerId, childId);
        return toResponse(findEnrollment(childId, courseId));
    }

    public List<EnrollmentResponse> getByChildId(UUID callerId, UUID childId) {
        requireSelfOrParent(callerId, childId);
        return enrollmentRepository.findAllByIdChildId(childId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Danh sách bé theo mã chỉ dành cho giáo viên của khóa (tên bé thuộc về phụ huynh).
    public List<EnrollmentResponse> getByCourseId(UUID callerId, UUID courseId) {
        boolean owner = courseRepository.findById(courseId)
                .map(course -> callerId.equals(course.getTeacherId()))
                .orElse(false);
        if (!owner) {
            throw forbidden("Chỉ giáo viên của khóa mới xem được danh sách học viên");
        }
        return enrollmentRepository.findAllByIdCourseId(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public boolean isEnrolled(UUID childId, UUID courseId) {
        return enrollmentRepository
                .existsByIdChildIdAndIdCourseIdAndStatusIgnoreCase(
                        childId, courseId, "active");
    }

    @Transactional
    public EnrollmentResponse updateStatus(
            UUID childId,
            UUID courseId,
            UpdateEnrollmentStatusRequest request) {

        String status = request.getStatus()
                .trim()
                .toLowerCase();

        if (!ALLOWED_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status must be active, inactive or cancelled");
        }

        Enrollment enrollment = findEnrollment(childId, courseId);
        enrollment.setStatus(status);

        return toResponse(enrollmentRepository.save(enrollment));
    }

    // Bé, hoặc phụ huynh sở hữu bé.
    private void requireSelfOrParent(UUID callerId, UUID childId) {
        if (callerId.equals(childId)
                || childRepository.findByUserIdAndParentId(childId, callerId).isPresent()) {
            return;
        }
        throw forbidden("Bạn không có quyền xem quyền học của bé này");
    }

    private AppError forbidden(String message) {
        return new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private Enrollment findEnrollment(
            UUID childId,
            UUID courseId) {

        return enrollmentRepository
                .findByIdChildIdAndIdCourseId(childId, courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Enrollment not found"));
    }

    private EnrollmentResponse toResponse(Enrollment enrollment) {
        return EnrollmentResponse.builder()
                .childId(enrollment.getId().getChildId())
                .courseId(enrollment.getId().getCourseId())
                .status(enrollment.getStatus())
                .build();
    }
}
