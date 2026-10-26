
package com.example.art_course.course.service;

import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.offer.repository.OfferRepository;

import com.example.art_course.course.dto.CreateCourseRequest;
import com.example.art_course.course.dto.UpdateCourseRequest;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CustomizeRequestRepository customizeRequestRepository;
    private final OfferRepository offerRepository;

    public CourseService(
            CourseRepository courseRepository,
            UserRepository userRepository,
            CustomizeRequestRepository customizeRequestRepository,
            OfferRepository offerRepository
    ) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.customizeRequestRepository = customizeRequestRepository;
        this.offerRepository = offerRepository;
    }

    @Transactional(readOnly = true)
    public List<Course> getAllCourses() {
        // Khóa làm riêng cho một yêu cầu customize không hiện công khai.
        return courseRepository.findAll().stream()
                .filter(c -> c.getCustomizeRequestId() == null)
                .toList();
    }

    @Transactional(readOnly = true)
    public Course getCourseById(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Course ID is required"
            );
        }

        return courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Course not found"
                ));
    }

    @Transactional(readOnly = true)
    public List<Course> getCoursesByTeacher(UUID teacherId) {
        if (teacherId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Teacher ID is required"
            );
        }

        return courseRepository.findAllByTeacherId(teacherId);
    }

    @Transactional
    public Course createCourse(
            CreateCourseRequest request,
            String userId
    ) {
        if (request.getName() == null
                || request.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Course name is required"
            );
        }

        User currentUser = getUserById(userId);
        requireCustomizeTeacher(request.getCustomizeRequestId(), currentUser.getId());
        Course course = Course.builder()
                // Never trust teacherId from the request body.
                .teacherId(currentUser.getId())
                .customizeRequestId(request.getCustomizeRequestId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .category(request.getCategory())
                .coverImageUrl(request.getCoverImageUrl())
                .price(request.getPrice())
                .status(request.getStatus())
                .build();

        return courseRepository.save(course);
    }

    @Transactional
    public Course updateCourse(
            UUID id,
            UpdateCourseRequest request,
            String userId
    ) {
        Course course = getCourseById(id);
        User currentUser = getUserById(userId);
        checkOwnership(course, currentUser);

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Course name cannot be blank"
                );
            }

            course.setName(request.getName().trim());
        }

        if (request.getCustomizeRequestId() != null
                && !request.getCustomizeRequestId().equals(course.getCustomizeRequestId())) {
            requireCustomizeTeacher(request.getCustomizeRequestId(), currentUser.getId());
            course.setCustomizeRequestId(
                    request.getCustomizeRequestId()
            );
        }

        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }

        if (request.getCategory() != null) {
            course.setCategory(request.getCategory());
        }

        if (request.getCoverImageUrl() != null) {
            course.setCoverImageUrl(request.getCoverImageUrl());
        }

        if (request.getPrice() != null) {
            course.setPrice(request.getPrice());
        }

        if (request.getStatus() != null) {
            course.setStatus(request.getStatus());
        }

        return courseRepository.save(course);
    }

    @Transactional
    public void deleteCourse(UUID id, String userId) {
        Course course = getCourseById(id);
        User currentUser = getUserById(userId);
        checkOwnership(course, currentUser);

        courseRepository.delete(course);
    }

    private User getUserById(String userId) {
        UUID id;

        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user ID in authentication"
            );
        }

        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));
    }
    // CUS-11: khóa học riêng chỉ do giáo viên có báo giá được chấp nhận làm, khi phụ huynh đã thanh toán (FUNDED).
    private void requireCustomizeTeacher(UUID requestId, UUID teacherId) {
        if (requestId == null) {
            return;
        }
        CustomizeRequest customize = customizeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customize request not found"));
        if (!"FUNDED".equalsIgnoreCase(customize.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Customize request is not paid yet");
        }
        boolean accepted = offerRepository.findAllByRequestIdAndTeacherId(requestId, teacherId).stream()
                .anyMatch(o -> "accepted".equalsIgnoreCase(o.getStatus()));
        if (!accepted) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You do not have the accepted offer for this request");
        }
    }

    private void checkOwnership(Course course, User user) {
        if (course.getTeacherId() == null
                || !course.getTeacherId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to modify this course"
            );
        }
    }
}
