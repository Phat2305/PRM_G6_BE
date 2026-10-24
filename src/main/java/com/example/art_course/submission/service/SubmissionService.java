
package com.example.art_course.submission.service;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.submission.dto.CreateSubmissionRequest;
import com.example.art_course.submission.dto.GradeSubmissionRequest;
import com.example.art_course.submission.dto.SubmissionResponse;
import com.example.art_course.submission.entity.Submission;
import com.example.art_course.submission.repository.SubmissionRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class SubmissionService {

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("submitted", "passed", "not_passed");

    private final SubmissionRepository submissionRepository;
    private final ChildRepository childRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            ChildRepository childRepository,
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            UserRepository userRepository) {

        this.submissionRepository = submissionRepository;
        this.childRepository = childRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SubmissionResponse create(
            CreateSubmissionRequest request,
            String username) {

        if (request.getChildId() == null
                || request.getLessonId() == null
                || request.getImageUrl() == null
                || request.getImageUrl().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Child, lesson and image URL are required");
        }

        User user = getUser(username);
        Child child = getChild(request.getChildId());

        checkChildAccess(child, user);
        getLesson(request.getLessonId());

        Submission submission = Submission.builder()
                .childId(request.getChildId())
                .lessonId(request.getLessonId())
                .imageUrl(request.getImageUrl().trim())
                .status("submitted")
                .build();

        return toResponse(submissionRepository.save(submission));
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getById(UUID id, String username) {
        Submission submission = findSubmission(id);
        User user = getUser(username);

        checkSubmissionAccess(submission, user);
        return toResponse(submission);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getByChildId(
            UUID childId, String username) {

        User user = getUser(username);
        Child child = getChild(childId);

        checkChildAccess(child, user);

        return submissionRepository.findAllByChildId(childId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getByLessonId(
            UUID lessonId, String username) {

        User user = getUser(username);
        Lesson lesson = getLesson(lessonId);

        checkTeacherOwnsLesson(lesson, user);

        return submissionRepository.findAllByLessonId(lessonId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getByStatus(
            String status, String username) {

        User user = getUser(username);
        requireRole(user, "ADMIN");

        String normalized = normalizeStatus(status);

        return submissionRepository.findAllByStatus(normalized)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SubmissionResponse grade(
            UUID id,
            GradeSubmissionRequest request,
            String username) {

        User user = getUser(username);
        Submission submission = findSubmission(id);

        Lesson lesson = getLesson(submission.getLessonId());
        checkTeacherOwnsLesson(lesson, user);

        if (request.getStatus() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Submission status is required");
        }

        String status = normalizeStatus(request.getStatus());

        if (!Set.of("passed", "not_passed").contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status must be passed or not_passed");
        }

        submission.setStatus(status);
        submission.setTeacherFeedback(request.getTeacherFeedback());
        submission.setBadge(request.getBadge());

        return toResponse(submissionRepository.save(submission));
    }

    private void checkSubmissionAccess(
            Submission submission, User user) {

        Child child = getChild(submission.getChildId());

        if (isChildOrParent(child, user)) {
            return;
        }

        Lesson lesson = getLesson(submission.getLessonId());

        if (isTeacherOfLesson(lesson, user)
                || hasRole(user, "ADMIN")) {
            return;
        }

        throw forbidden();
    }

    private void checkChildAccess(Child child, User user) {
        if (!isChildOrParent(child, user)
                && !hasRole(user, "ADMIN")) {
            throw forbidden();
        }
    }

    private boolean isChildOrParent(Child child, User user) {
        return user.getId().equals(child.getUserId())
                || user.getId().equals(child.getParentId());
    }

    private void checkTeacherOwnsLesson(
            Lesson lesson, User user) {

        if (!isTeacherOfLesson(lesson, user)
                && !hasRole(user, "ADMIN")) {
            throw forbidden();
        }
    }

    private boolean isTeacherOfLesson(
            Lesson lesson, User user) {

        if (lesson.getCourseId() == null) {
            return false;
        }

        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Course not found"));

        return course.getTeacherId() != null
                && course.getTeacherId().equals(user.getId());
    }

    private User getUser(String username) {
        return userRepository.findByPrincipal(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));
    }

    private Child getChild(UUID childId) {
        return childRepository.findById(childId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Child not found"));
    }

    private Lesson getLesson(UUID lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lesson not found"));
    }

    private Submission findSubmission(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Submission ID is required");
        }

        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Submission not found"));
    }

    private void requireRole(User user, String role) {
        if (!hasRole(user, role)) {
            throw forbidden();
        }
    }

    private boolean hasRole(User user, String role) {
        return user.getRole() != null
                && user.getRole().equalsIgnoreCase(role);
    }

    private String normalizeStatus(String status) {
        if (status == null
                || !ALLOWED_STATUSES.contains(
                status.trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid submission status");
        }

        return status.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not allowed to access this submission");
    }

    private SubmissionResponse toResponse(Submission submission) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .childId(submission.getChildId())
                .lessonId(submission.getLessonId())
                .imageUrl(submission.getImageUrl())
                .status(submission.getStatus())
                .teacherFeedback(submission.getTeacherFeedback())
                .badge(submission.getBadge())
                .build();
    }
}
