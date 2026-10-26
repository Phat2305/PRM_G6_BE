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
import com.example.art_course.course.service.CourseReviewPolicy;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.entity.Message;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.messaging.repository.MessageRepository;
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

// CUS-11/12: giáo viên gửi khóa học riêng cho Staff duyệt (yêu cầu vẫn FUNDED, khóa PENDING_REVIEW); Staff duyệt thì
// yêu cầu sang DELIVERED (CourseModerationService). Phụ huynh xem, chưa đồng ý thì nêu lý do (gửi cho giáo viên qua
// tin nhắn, yêu cầu về FUNDED để sửa) hoặc gán cho bé và xác nhận hoàn thành (DELIVERED → COMPLETED): bé được cấp
// quyền học, tiền ký quỹ được xét giải ngân. Không thống nhất được thì phụ huynh xin hoàn tiền.
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
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public record LessonView(String title, boolean trial, boolean hasAssignment) {}

    // reviewStatus = trạng thái duyệt của khóa (PENDING_REVIEW/REJECTED/PUBLISHED...), rejectionCount/reviewNote = lần
    // và lý do từ chối gần nhất; orderId/orderDetailId là dòng đơn đã thanh toán để phụ huynh xin hoàn tiền.
    public record DeliveryView(UUID requestId, String status, UUID courseId, String courseName,
                               String description, String category, String teacherName,
                               List<LessonView> lessons, String reviewStatus, int rejectionCount, String reviewNote,
                               UUID orderId, UUID orderDetailId) {}

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
        // Chỉ gửi được bản nháp hoặc bản bị Staff từ chối (chưa đủ số lần); đang chờ duyệt hay đã duyệt thì không.
        if (!CourseReviewPolicy.is(course, CourseReviewPolicy.DRAFT)
                && !CourseReviewPolicy.is(course, CourseReviewPolicy.REJECTED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khóa này đang chờ Staff duyệt hoặc đã được duyệt");
        }
        // Gắn khóa với yêu cầu trước khi kiểm tra: khóa customize không bị bắt giá khóa lớn hơn 0.
        course.setCustomizeRequestId(requestId);
        // Có bài học, mọi video xử lý xong, chưa bị từ chối đủ lần: Staff duyệt trước khi phụ huynh thấy khóa.
        CourseReviewPolicy.requireSubmittable(course, lessonRepository.findAllByCourseId(courseId));

        course.setStatus(CourseReviewPolicy.PENDING_REVIEW);
        courseRepository.save(course);
        return view(request, course);
    }

    @Transactional(readOnly = true)
    public DeliveryView delivery(UUID requestId, String principal) {
        User user = user(principal);
        CustomizeRequest request = request(requestId);
        boolean parent = user.getId().equals(request.getParentId());
        if (!parent) {
            requireAcceptedTeacher(request, user);
        } else if (!"DELIVERED".equalsIgnoreCase(request.getStatus())
                && !"COMPLETED".equalsIgnoreCase(request.getStatus())
                && !("CANCELLED".equalsIgnoreCase(request.getStatus()) && paidLineOrNull(requestId) != null)) {
            // Phụ huynh chỉ thấy khóa sau khi Staff duyệt (yêu cầu sang DELIVERED); yêu cầu đã trả tiền mà bị hủy
            // (khóa không qua kiểm duyệt) thì xem được để biết đơn cần hoàn tiền.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not delivered yet");
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

    /**
     * Phụ huynh chưa đồng ý với bản bàn giao (DELIVERED): nêu lý do. Yêu cầu về FUNDED và khóa về DRAFT để giáo viên
     * sửa rồi gửi Staff duyệt lại; lý do được gửi cho giáo viên qua cuộc trò chuyện của yêu cầu. Không thống nhất được
     * thì phụ huynh xin hoàn tiền (POST /api/v1/refunds/request cho dòng đơn của yêu cầu).
     */
    @Transactional
    public DeliveryView rejectDelivery(UUID requestId, String reason, String principal) {
        User parent = user(principal);
        CustomizeRequest request = request(requestId);
        if (!parent.getId().equals(request.getParentId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This request is not yours");
        }
        if (!"DELIVERED".equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only delivered requests can be sent back");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required");
        }
        Course course = courseRepository.findFirstByCustomizeRequestId(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not delivered yet"));

        String why = reason.trim();
        request.setStatus("FUNDED");
        requestRepository.save(request);
        course.setStatus(CourseReviewPolicy.DRAFT);
        course.setRejectionCount(0);
        course.setReviewNote("Phụ huynh chưa đồng ý: " + why);
        courseRepository.save(course);

        Conversation conversation = conversationRepository
                .findFirstByContextTypeAndContextIdAndTeacherId("CUSTOMIZE_REQUEST", requestId, course.getTeacherId())
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .parentId(parent.getId())
                        .teacherId(course.getTeacherId())
                        .contextType("CUSTOMIZE_REQUEST")
                        .contextId(requestId)
                        .build()));
        String text = "Mình chưa đồng ý với bản bàn giao: " + why;
        messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .senderId(parent.getId())
                .content(text)
                .isOffer(false)
                .build());
        notificationService.notifyNewMessage(course.getTeacherId(), conversation.getId(), text);
        notificationService.notifyCustomizeDeliveryRejected(requestId, course.getTeacherId(), course.getName(), why);
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

    private OrderDetail paidLineOrNull(UUID requestId) {
        try {
            return paidLine(requestId);
        } catch (ResponseStatusException e) {
            return null;
        }
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
        OrderDetail line = paidLineOrNull(request.getId());
        return new DeliveryView(request.getId(), request.getStatus(), course.getId(), course.getName(),
                course.getDescription(), course.getCategory(), teacherNames.byId(course.getTeacherId()), lessons,
                course.getStatus(), course.getRejectionCount(), course.getReviewNote(),
                line == null ? null : line.getOrderId(), line == null ? null : line.getId());
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
