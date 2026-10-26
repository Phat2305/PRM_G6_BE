
package com.example.art_course.course.service;

import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.offer.repository.OfferRepository;

import com.example.art_course.course.dto.CreateCourseRequest;
import com.example.art_course.course.dto.UpdateCourseRequest;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.repository.LessonRepository;
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
    private final LessonRepository lessonRepository;

    public CourseService(
            CourseRepository courseRepository,
            UserRepository userRepository,
            CustomizeRequestRepository customizeRequestRepository,
            OfferRepository offerRepository,
            LessonRepository lessonRepository
    ) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.customizeRequestRepository = customizeRequestRepository;
        this.offerRepository = offerRepository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional(readOnly = true)
    public List<Course> getAllCourses() {
        // Chỉ khóa đã được Staff duyệt; khóa làm riêng cho một yêu cầu customize không hiện công khai.
        return courseRepository.findAllByStatus(CourseReviewPolicy.PUBLISHED).stream()
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
        requireTeacher(currentUser);
        requireCustomizeTeacher(request.getCustomizeRequestId(), currentUser.getId());
        // Khóa mới luôn là bản nháp: chưa có bài học nên chưa gửi duyệt được, PUBLISHED/REJECTED chỉ Staff đặt.
        String status = request.getStatus() == null || request.getStatus().isBlank()
                ? CourseReviewPolicy.DRAFT : request.getStatus().trim().toUpperCase();
        if (CourseReviewPolicy.PUBLISHED.equals(status) || CourseReviewPolicy.REJECTED.equals(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Staff được công khai hoặc từ chối khóa học");
        }
        if (!CourseReviewPolicy.DRAFT.equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Khóa mới phải ở trạng thái DRAFT, thêm bài học rồi mới gửi duyệt");
        }
        Course course = Course.builder()
                // Never trust teacherId from the request body.
                .teacherId(currentUser.getId())
                .customizeRequestId(request.getCustomizeRequestId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .category(request.getCategory())
                .coverImageUrl(request.getCoverImageUrl())
                .price(request.getPrice())
                .status(status)
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

        boolean changed = false;

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Course name cannot be blank"
                );
            }

            changed |= !request.getName().trim().equals(course.getName());
            course.setName(request.getName().trim());
        }

        if (request.getCustomizeRequestId() != null
                && !request.getCustomizeRequestId().equals(course.getCustomizeRequestId())) {
            requireCustomizeTeacher(request.getCustomizeRequestId(), currentUser.getId());
            course.setCustomizeRequestId(
                    request.getCustomizeRequestId()
            );
            changed = true;
        }

        if (request.getDescription() != null) {
            changed |= !request.getDescription().equals(course.getDescription());
            course.setDescription(request.getDescription());
        }

        if (request.getCategory() != null) {
            changed |= !request.getCategory().equals(course.getCategory());
            course.setCategory(request.getCategory());
        }

        if (request.getCoverImageUrl() != null) {
            changed |= !request.getCoverImageUrl().equals(course.getCoverImageUrl());
            course.setCoverImageUrl(request.getCoverImageUrl());
        }

        if (request.getPrice() != null) {
            changed |= course.getPrice() == null || request.getPrice().compareTo(course.getPrice()) != 0;
            course.setPrice(request.getPrice());
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            applyTeacherStatus(course, request.getStatus().trim().toUpperCase(), changed);
        } else if (changed) {
            CourseReviewPolicy.onContentChanged(course);
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

    // Giáo viên chỉ được đặt DRAFT (nháp/rút lại/gỡ) hoặc PENDING_REVIEW (gửi duyệt); PUBLISHED, REJECTED chỉ Staff đặt.
    private void applyTeacherStatus(Course course, String target, boolean contentChanged) {
        if (CourseReviewPolicy.DRAFT.equals(target)) {
            if (CourseReviewPolicy.isFinallyRejected(course)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Khóa đã bị từ chối " + CourseReviewPolicy.MAX_REJECTIONS + " lần nên không đổi trạng thái được");
            }
            if (CourseReviewPolicy.is(course, CourseReviewPolicy.PUBLISHED) && CourseReviewPolicy.isCustomize(course)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Khóa đã được bàn giao cho phụ huynh nên không gỡ về bản nháp được");
            }
            course.setStatus(CourseReviewPolicy.DRAFT);
        } else if (CourseReviewPolicy.PENDING_REVIEW.equals(target)) {
            if (CourseReviewPolicy.is(course, CourseReviewPolicy.PUBLISHED) && !contentChanged) {
                return; // đang công khai và không đổi gì: không cần duyệt lại
            }
            if (!CourseReviewPolicy.is(course, CourseReviewPolicy.PENDING_REVIEW)) {
                CourseReviewPolicy.requireSubmittable(course, lessonRepository.findAllByCourseId(course.getId()));
            }
            course.setStatus(CourseReviewPolicy.PENDING_REVIEW);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ Staff được công khai hoặc từ chối khóa học");
        }
    }

    private void requireTeacher(User user) {
        if (user.getRole() == null || !"TEACHER".equalsIgnoreCase(user.getRole().trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ giáo viên được tạo khóa học");
        }
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
