        package com.example.art_course.enrollment.service;

import com.example.art_course.enrollment.dto.CreateEnrollmentRequest;
import com.example.art_course.enrollment.dto.EnrollmentResponse;
import com.example.art_course.enrollment.dto.UpdateEnrollmentStatusRequest;
import com.example.art_course.enrollment.entity.Enrollment;
import com.example.art_course.enrollment.entity.EnrollmentId;
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

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("active", "inactive", "cancelled");

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional
    public EnrollmentResponse create(
            CreateEnrollmentRequest request) {

        EnrollmentId id = new EnrollmentId(
                request.getChildId(),
                request.getCourseId()
        );

        if (enrollmentRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Child is already enrolled in this course");
        }

        Enrollment enrollment = Enrollment.builder()
                .id(id)
                .status("active")
                .build();

        return toResponse(enrollmentRepository.save(enrollment));
    }

    public EnrollmentResponse getById(
            UUID childId,
            UUID courseId) {

        return toResponse(findEnrollment(childId, courseId));
    }

    public List<EnrollmentResponse> getByChildId(UUID childId) {
        return enrollmentRepository.findAllByIdChildId(childId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EnrollmentResponse> getByCourseId(UUID courseId) {
        return enrollmentRepository.findAllByIdCourseId(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public boolean isEnrolled(UUID childId, UUID courseId) {
        return enrollmentRepository
                .existsByIdChildIdAndIdCourseIdAndStatus(
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
