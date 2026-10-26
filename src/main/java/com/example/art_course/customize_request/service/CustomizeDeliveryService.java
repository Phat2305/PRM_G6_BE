package com.example.art_course.customize_request.service;

import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.modules.finance.service.EscrowReleaseService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// CUS-11/12: giáo viên bàn giao khóa học riêng (FUNDED → DELIVERED); phụ huynh xem, gán cho bé
// và xác nhận hoàn thành (DELIVERED → COMPLETED): bé được cấp quyền học, tiền ký quỹ được xét giải ngân.
@Service
@RequiredArgsConstructor
public class CustomizeDeliveryService {

    private final CustomizeRequestRepository requestRepository;
    private final OfferRepository offerRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final ChildRepository childRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderRepository orderRepository;
    private final IEnrollmentContract enrollmentContract;
    private final EscrowReleaseService escrowReleaseService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final TeacherNames teacherNames;

    public record LessonView(String title, boolean trial, boolean hasAssignment) {}

    public record DeliveryView(UUID requestId, String status, UUID courseId, String courseName,
                               String description, String category, String teacherName,
                               List<LessonView> lessons) {}

    @Transactional
    public DeliveryView deliver(UUID requestId, UUID courseId, String principal) {
        User teacher = user(principal);
        CustomizeRequest request = request(requestId);
        requireAcceptedTeacher(request, teacher);
        if (!"FUNDED".equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only paid (FUNDED) requests can be delivered");
        }
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId is required");
        }
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        if (!teacher.getId().equals(course.getTeacherId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is not yours");
        }
        if (course.getCustomizeRequestId() != null && !requestId.equals(course.getCustomizeRequestId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This course belongs to another request");
        }
        if (lessonRepository.findAllByCourseId(courseId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one lesson before delivering");
        }
        // Khóa riêng không vào danh mục công khai (lọc theo customize_request_id) nên để PUBLISHED cho bé học được.
        course.setCustomizeRequestId(requestId);
        course.setStatus("PUBLISHED");
        courseRepository.save(course);
        request.setStatus("DELIVERED");
        requestRepository.save(request);
        notificationService.notifyCustomizeDelivered(requestId, request.getParentId(), course.getName());
        return view(request, course);
    }

    @Transactional(readOnly = true)
    public DeliveryView delivery(UUID requestId, String principal) {
        User user = user(principal);
        CustomizeRequest request = request(requestId);
        boolean parent = user.getId().equals(request.getParentId());
        if (!parent) {
            requireAcceptedTeacher(request, user);
        }
        Course course = courseRepository.findFirstByCustomizeRequestId(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not delivered yet"));
        return view(request, course);
    }

    @Transactional
    public DeliveryView complete(UUID requestId, UUID childId, String principal) {
        User parent = user(principal);
        CustomizeRequest request = request(requestId);
        if (!parent.getId().equals(request.getParentId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This request is not yours");
        }
        if (!"DELIVERED".equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only delivered requests can be completed");
        }
        if (childId == null || childRepository.findByUserIdAndParentId(childId, parent.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of your children");
        }
        Course course = courseRepository.findFirstByCustomizeRequestId(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not delivered yet"));

        OrderDetail line = paidLine(requestId);
        if (!enrollmentContract.isEnrolled(childId, course.getId())) {
            enrollmentContract.grantAccess(childId, course.getId(), line.getOrderId());
        }
        line.setChildId(childId);
        line.setDeliveryStatus(DeliveryStatus.COMPLETED);
        line.setConfirmedDeliveredAt(Instant.now());
        orderDetailRepository.save(line);

        request.setStatus("COMPLETED");
        requestRepository.save(request);
        // Giải ngân ngay nếu mọi dòng của đơn đã đủ điều kiện (#26); chưa đủ thì job định kỳ xét lại.
        escrowReleaseService.tryReleaseEscrow(line.getOrderId());
        notificationService.notifyCustomizeCompleted(requestId, course.getTeacherId(), course.getName());
        return view(request, course);
    }

    // Dòng đơn CUSTOMIZE của đơn đã thanh toán (đơn hết hạn/hủy bỏ qua).
    private OrderDetail paidLine(UUID requestId) {
        for (OrderDetail detail : orderDetailRepository.findByCustomizeRequestId(requestId)) {
            Order order = orderRepository.findById(detail.getOrderId()).orElse(null);
            if (order != null && order.getStatus() == OrderStatus.PAID) {
                return detail;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "No paid order for this request");
    }

    private void requireAcceptedTeacher(CustomizeRequest request, User teacher) {
        boolean accepted = offerRepository.findAllByRequestIdAndTeacherId(request.getId(), teacher.getId()).stream()
                .anyMatch(o -> "accepted".equalsIgnoreCase(o.getStatus()));
        if (!accepted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have the accepted offer for this request");
        }
    }

    private DeliveryView view(CustomizeRequest request, Course course) {
        List<LessonView> lessons = lessonRepository.findAllByCourseId(course.getId()).stream()
                .map((Lesson l) -> new LessonView(l.getTitle(),
                        Boolean.TRUE.equals(l.getIsTrial()), Boolean.TRUE.equals(l.getHasAssignment())))
                .toList();
        return new DeliveryView(request.getId(), request.getStatus(), course.getId(), course.getName(),
                course.getDescription(), course.getCategory(), teacherNames.byId(course.getTeacherId()), lessons);
    }

    private CustomizeRequest request(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customize request not found"));
    }

    private User user(String principal) {
        return userRepository.findByPrincipal(principal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
