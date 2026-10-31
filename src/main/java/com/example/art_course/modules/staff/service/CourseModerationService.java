package com.example.art_course.modules.staff.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.contracts.IModerationContract;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.course.service.CourseReviewPolicy;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.customize_request.repository.CustomizeDeliveryHistoryRepository;
import com.example.art_course.customize_request.entity.CustomizeDeliveryHistory;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.staff.dto.CourseReviewDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * STF-01/07: Staff duyệt khóa học. Khóa thường được duyệt thì công khai; khóa customize được duyệt thì yêu cầu
 * sang DELIVERED để phụ huynh xem và nhận (Staff duyệt trước, phụ huynh sau). Bị từ chối thì giáo viên sửa rồi gửi lại,
 * tối đa CourseReviewPolicy.MAX_REJECTIONS lần từ chối; khóa customize bị từ chối đủ lần thì yêu cầu bị hủy để phụ huynh xin hoàn tiền.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseModerationService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final CustomizeRequestRepository requestRepository;
    private final IModerationContract moderationContract;
    private final NotificationService notificationService;
    private final TeacherNames names;
    private final com.example.art_course.modules.order.repository.OrderDetailRepository orderDetails;
    private final CustomizeDeliveryHistoryRepository deliveryHistoryRepository;

    @Transactional(readOnly = true)
    public List<CourseReviewDtos.Item> pending() {
        return items(courseRepository.findAllByStatus(CourseReviewPolicy.PENDING_REVIEW));
    }

    // Khóa đang công khai để Staff gỡ khi vi phạm; khóa customize là của riêng một phụ huynh nên không nằm trong danh mục.
    @Transactional(readOnly = true)
    public List<CourseReviewDtos.Item> published() {
        return items(courseRepository.findAllByStatus(CourseReviewPolicy.PUBLISHED).stream()
                .filter(c -> !CourseReviewPolicy.isCustomize(c)).toList());
    }

    private List<CourseReviewDtos.Item> items(List<Course> courses) {
        Map<UUID, String> teachers = names.byIds(courses.stream().map(Course::getTeacherId).distinct().toList());
        return courses.stream().map(c -> {
            List<Lesson> lessons = lessonRepository.findAllByCourseId(c.getId());
            int ready = (int) lessons.stream().filter(CourseReviewPolicy::videoReady).count();
            return new CourseReviewDtos.Item(c.getId(), c.getName(), c.getTeacherId(), teachers.get(c.getTeacherId()),
                    c.getCategory(), c.getPrice(), CourseReviewPolicy.isCustomize(c), c.getRejectionCount(),
                    lessons.size(), ready);
        }).toList();
    }

    @Transactional(readOnly = true)
    public CourseReviewDtos.Detail detail(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học"));
        if (CourseReviewPolicy.is(course, CourseReviewPolicy.DRAFT)
                || CourseReviewPolicy.is(course, CourseReviewPolicy.DELETED)) {
            throw new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học");
        }
        List<CourseReviewDtos.LessonItem> lessons = lessonRepository.findAllByCourseId(courseId).stream()
                .map(l -> new CourseReviewDtos.LessonItem(l.getId(), l.getTitle(), Boolean.TRUE.equals(l.getIsTrial()),
                        Boolean.TRUE.equals(l.getHasAssignment()), l.getVideoStatus(), l.getVideoUrl()))
                .toList();
        return new CourseReviewDtos.Detail(course.getId(), course.getName(), course.getDescription(),
                course.getCategory(), course.getPrice(), course.getStatus(), course.getTeacherId(),
                names.byId(course.getTeacherId()), CourseReviewPolicy.isCustomize(course),
                course.getRejectionCount(), course.getReviewNote(), lessons);
    }

    @Transactional
    public void approve(UUID courseId, UUID staffId) {
        Course course = lockPending(courseId);
        List<Lesson> courseLessons = lessonRepository.findAllByCourseId(courseId);
        CourseReviewPolicy.requireReviewable(course, courseLessons);

        CustomizeRequest request = null;
        if (CourseReviewPolicy.isCustomize(course)) {
            request = requestRepository.findById(course.getCustomizeRequestId())
                    .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu của khóa này"));
            if (!"FUNDED".equalsIgnoreCase(request.getStatus())) {
                throw new AppError(HttpStatus.CONFLICT, "REQUEST_NOT_FUNDED", "Yêu cầu của khóa này không còn ở trạng thái đã thanh toán");
            }
        }

        course.setStatus(CourseReviewPolicy.PUBLISHED);
        course.setRejectionCount(0);
        course.setReviewNote(null);
        courseRepository.save(course);

        if (request != null) {
            request.setStatus("DELIVERED");
            requestRepository.save(request);
            var deliveredAt = java.time.Instant.now();
            deliveryHistoryRepository.save(CustomizeDeliveryHistory.builder()
                    .requestId(request.getId()).courseId(courseId).courseName(course.getName())
                    .lessonCount(courseLessons.size()).deliveredAt(deliveredAt).build());
            // CUS-12: mốc X ngày bắt đầu từ lần bàn giao đã được Staff duyệt.
            for (var line : orderDetails.findByCustomizeRequestId(request.getId())) {
                line.setDeliveredAt(deliveredAt);
                orderDetails.save(line);
            }
            notificationService.notifyCustomizeDelivered(request.getId(), request.getParentId(), course.getName());
        } else {
            notificationService.notifyCourseApproved(course.getId(), course.getTeacherId(), course.getName());
        }
        moderationContract.recordModerationReview(staffId, "COURSE", courseId, "APPROVED", null);
        log.info("[COURSE-APPROVE] Staff {} đã duyệt khóa {}", staffId, courseId);
    }

    @Transactional
    public void reject(UUID courseId, UUID staffId, String reason) {
        Course course = lockPending(courseId);

        course.setStatus(CourseReviewPolicy.REJECTED);
        course.setRejectionCount(course.getRejectionCount() + 1);
        course.setReviewNote(reason);
        courseRepository.save(course);

        int remaining = Math.max(0, CourseReviewPolicy.MAX_REJECTIONS - course.getRejectionCount());
        moderationContract.recordModerationReview(staffId, "COURSE", courseId, "REJECTED", reason);
        notificationService.notifyCourseRejected(course.getId(), course.getTeacherId(), course.getName(), reason, remaining);

        // Khóa riêng bị từ chối đủ lần: không bàn giao được nữa, hủy yêu cầu để phụ huynh xin hoàn tiền.
        if (remaining == 0 && CourseReviewPolicy.isCustomize(course)) {
            requestRepository.findById(course.getCustomizeRequestId()).ifPresent(request -> {
                if ("FUNDED".equalsIgnoreCase(request.getStatus())) {
                    request.setStatus("CANCELLED");
                    requestRepository.save(request);
                    notificationService.notifyCustomizeFailed(request.getId(), request.getParentId(), course.getName());
                }
            });
        }
        log.info("[COURSE-REJECT] Staff {} đã từ chối khóa {} (lần {})", staffId, courseId, course.getRejectionCount());
    }

    // STF-06: gỡ khóa đang công khai (không xóa): về HIDDEN kèm lý do, khỏi danh mục và không mua mới được;
    // người đã mua vẫn học được. Giáo viên sửa rồi gửi duyệt lại như khóa bị từ chối.
    @Transactional
    public void hide(UUID courseId, UUID staffId, String reason) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học"));
        if (!CourseReviewPolicy.is(course, CourseReviewPolicy.PUBLISHED) || CourseReviewPolicy.isCustomize(course)) {
            throw new AppError(HttpStatus.CONFLICT, "INVALID_STATUS", "Chỉ gỡ được khóa đang công khai trên danh mục");
        }
        course.setStatus(CourseReviewPolicy.HIDDEN);
        course.setReviewNote(reason);
        courseRepository.save(course);
        moderationContract.recordModerationReview(staffId, "COURSE", courseId, "FLAGGED", reason);
        notificationService.notifyCourseHidden(course.getId(), course.getTeacherId(), course.getName(), reason);
        log.info("[COURSE-HIDE] Staff {} đã gỡ khóa {}", staffId, courseId);
    }

    // Khóa dòng để hai Staff cùng duyệt một khóa thì chạy lần lượt; chỉ khóa đang chờ duyệt mới quyết định được.
    private Course lockPending(UUID courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học"));
        if (!CourseReviewPolicy.is(course, CourseReviewPolicy.PENDING_REVIEW)) {
            throw new AppError(HttpStatus.CONFLICT, "INVALID_STATUS", "Khóa học không ở trạng thái chờ duyệt (PENDING_REVIEW)");
        }
        return course;
    }
}
