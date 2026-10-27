
package com.example.art_course.submission.service;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.enrollment.repository.EnrollmentRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.submission.dto.GradeSubmissionRequest;
import com.example.art_course.submission.dto.SubmissionResponse;
import com.example.art_course.submission.entity.Submission;
import com.example.art_course.submission.repository.SubmissionRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import com.example.art_course.video.service.R2StorageService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final EnrollmentRepository enrollmentRepository;
    private final R2StorageService r2StorageService;

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    public SubmissionService(
            SubmissionRepository submissionRepository,
            ChildRepository childRepository,
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            EnrollmentRepository enrollmentRepository,
            R2StorageService r2StorageService) {

        this.submissionRepository = submissionRepository;
        this.childRepository = childRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.r2StorageService = r2StorageService;
    }

    // Bé tự nộp ảnh bài vẽ (ASG-01/02): chỉ bé đã có quyền học khóa chứa bài học. Ảnh lưu R2 key submissions/{id}.
    @Transactional
    public SubmissionResponse upload(
            UUID lessonId, MultipartFile file, String username) {

        User user = getUser(username);
        if (!hasRole(user, "CHILD")) {
            throw forbidden();
        }
        Child child = childRepository.findByUserId(user.getId())
                .orElseThrow(() -> forbidden());
        Lesson lesson = getLesson(lessonId);
        if (lesson.getCourseId() == null
                || !enrollmentRepository.existsByIdChildIdAndIdCourseId(
                        child.getUserId(), lesson.getCourseId())) {
            throw forbidden();
        }

        byte[] data = readImage(file);

        Submission saved = submissionRepository.save(Submission.builder()
                .childId(child.getUserId())
                .lessonId(lessonId)
                .imageUrl("pending")
                .status("submitted")
                .build());
        saved.setImageUrl("/api/submissions/" + saved.getId() + "/image");
        // Lỗi R2 ném ra ngoài nên giao dịch hoàn tác, không để lại bài nộp không có ảnh.
        r2StorageService.uploadBytes(imageKey(saved.getId()), data, file.getContentType());
        return toResponse(submissionRepository.save(saved), child.getDisplayName());
    }

    private static byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppError(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "Chưa chọn ảnh");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new AppError(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE", "Ảnh tối đa 10 MB");
        }
        String type = file.getContentType();
        if (type == null || !IMAGE_TYPES.contains(type)) {
            throw new AppError(HttpStatus.BAD_REQUEST, "IMAGE_TYPE", "Chỉ nhận ảnh JPG, PNG hoặc WEBP");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new AppError(HttpStatus.BAD_REQUEST, "IMAGE_READ", "Không đọc được ảnh");
        }
    }

    // Chỉ bé chủ bài, phụ huynh của bé, giáo viên của khóa và Admin xem được ảnh.
    @Transactional(readOnly = true)
    public ResponseInputStream<GetObjectResponse> image(UUID id, String username) {
        Submission submission = findSubmission(id);
        checkSubmissionAccess(submission, getUser(username));
        return r2StorageService.getObject(imageKey(id));
    }

    private static String imageKey(UUID id) {
        return "submissions/" + id;
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getById(UUID id, String username) {
        Submission submission = findSubmission(id);
        User user = getUser(username);

        checkSubmissionAccess(submission, user);
        return toResponses(List.of(submission), user).get(0);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getByChildId(
            UUID childId, String username) {

        User user = getUser(username);
        Child child = getChild(childId);

        checkChildAccess(child, user);

        return toResponses(submissionRepository.findAllByChildId(childId), user);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getByLessonId(
            UUID lessonId, String username) {

        User user = getUser(username);
        Lesson lesson = getLesson(lessonId);

        checkTeacherOwnsLesson(lesson, user);

        return toResponses(submissionRepository.findAllByLessonId(lessonId), user);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getByStatus(
            String status, String username) {

        User user = getUser(username);
        requireRole(user, "ADMIN");

        String normalized = normalizeStatus(status);

        return toResponses(submissionRepository.findAllByStatus(normalized), user);
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
        submission.setGradedAt(Instant.now());

        return toResponses(List.of(submissionRepository.save(submission)), user).get(0);
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

    // Một lần hỏi tên bé cho cả danh sách, tránh N+1. Giáo viên chỉ thấy tên khi phụ huynh bật shareInfo (ASG-09).
    private List<SubmissionResponse> toResponses(List<Submission> list, User viewer) {
        Map<UUID, Child> children = new HashMap<>();
        childRepository.findAllById(
                list.stream().map(Submission::getChildId).distinct().toList())
                .forEach(c -> children.put(c.getUserId(), c));
        return list.stream()
                .map(x -> toResponse(x, visibleName(children.get(x.getChildId()), viewer)))
                .toList();
    }

    public static String visibleName(Child child, User viewer) {
        if (child == null) {
            return null;
        }
        boolean owner = viewer.getId().equals(child.getUserId())
                || viewer.getId().equals(child.getParentId());
        return owner || child.isShareInfo() ? child.getDisplayName() : null;
    }

    private SubmissionResponse toResponse(Submission submission, String childName) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .childId(submission.getChildId())
                .lessonId(submission.getLessonId())
                .imageUrl(submission.getImageUrl())
                .status(submission.getStatus())
                .teacherFeedback(submission.getTeacherFeedback())
                .badge(submission.getBadge())
                .childName(childName)
                .createdAt(submission.getCreatedAt())
                .gradedAt(submission.getGradedAt())
                .build();
    }
}
