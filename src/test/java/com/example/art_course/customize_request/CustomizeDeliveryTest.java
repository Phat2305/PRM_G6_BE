package com.example.art_course.customize_request;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.customize_request.service.CustomizeDeliveryService;
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
import com.example.art_course.modules.order.entity.ChildrenCourse;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.offer.entity.Offer;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// CUS-11/12: giáo viên gửi khóa cho Staff duyệt, phụ huynh nhận hoặc chưa đồng ý, xác nhận hoàn thành.
class CustomizeDeliveryTest {

    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final com.example.art_course.customize_request.repository.CustomizeDeliveryHistoryRepository history =
            mock(com.example.art_course.customize_request.repository.CustomizeDeliveryHistoryRepository.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final com.example.art_course.modules.order.repository.ChildrenCourseRepository childrenCourses =
            mock(com.example.art_course.modules.order.repository.ChildrenCourseRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final IEnrollmentContract enrollment = mock(IEnrollmentContract.class);
    private final EscrowReleaseService escrow = mock(EscrowReleaseService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final CustomizeDeliveryService service = new CustomizeDeliveryService(requests, history, offers, courses,
            lessons, children, details, childrenCourses, orders, enrollment, escrow, notifications, users, mock(TeacherNames.class),
            conversations, messages);

    private final UUID parentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final CustomizeRequest request = CustomizeRequest.builder()
            .id(UUID.randomUUID()).parentId(parentId).title("Vẽ mèo").status("FUNDED").build();
    private final Course course = Course.builder()
            .id(UUID.randomUUID()).teacherId(teacherId).name("Vẽ mèo cho bé").status("DRAFT").build();

    private String login(UUID id, String role) {
        when(users.findByPrincipal(id.toString()))
                .thenReturn(Optional.of(new User(id, "u", "h", role, "ACTIVE", null, null)));
        return id.toString();
    }

    private Lesson readyLesson() {
        return Lesson.builder().title("Bài 1").isTrial(true).hasAssignment(false).videoStatus("READY").build();
    }

    private void setup() {
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
        when(offers.findAllByRequestIdAndTeacherId(request.getId(), teacherId))
                .thenReturn(List.of(Offer.builder().teacherId(teacherId).status("accepted").build()));
        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of(readyLesson()));
    }

    @Test
    void deliver_sendsCourseToStaffReview_andRequestStaysFunded() {
        setup();
        var view = service.deliver(request.getId(), course.getId(), login(teacherId, "TEACHER"));

        assertEquals("FUNDED", request.getStatus());
        assertEquals(request.getId(), course.getCustomizeRequestId());
        assertEquals("PENDING_REVIEW", course.getStatus());
        assertEquals("PENDING_REVIEW", view.reviewStatus());
        assertEquals(1, view.lessons().size());
        // Phụ huynh chỉ được báo sau khi Staff duyệt.
        verify(notifications, never()).notifyCustomizeDelivered(any(), any(), anyString());
    }

    @Test
    void deliver_rejectsOtherTeacher_unpaidRequest_emptyCourse_andVideosNotReady() {
        setup();
        String other = login(UUID.randomUUID(), "TEACHER");
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), other));

        String teacher = login(teacherId, "TEACHER");
        request.setStatus("ACCEPTED");
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), teacher));

        request.setStatus("FUNDED");
        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), teacher));

        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of(
                Lesson.builder().title("Bài 2").videoStatus("PROCESSING").build()));
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), teacher));
        assertEquals("DRAFT", course.getStatus());
    }

    @Test
    void deliver_rejectsCourseAlreadyInReviewOrPublished_andFinallyRejectedCourse() {
        setup();
        String teacher = login(teacherId, "TEACHER");
        course.setStatus("PENDING_REVIEW");
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), teacher));
        course.setStatus("PUBLISHED");
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), teacher));

        course.setStatus("REJECTED");
        course.setRejectionCount(3);
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), teacher));

        // Bị từ chối chưa đủ 3 lần thì sửa rồi gửi lại được.
        course.setRejectionCount(2);
        service.deliver(request.getId(), course.getId(), teacher);
        assertEquals("PENDING_REVIEW", course.getStatus());
    }

    @Test
    void deliverCannotSwitchToAnotherCourseForTheSameRequest() {
        setup();
        when(courses.findFirstByCustomizeRequestId(request.getId())).thenReturn(Optional.of(
                Course.builder().id(UUID.randomUUID()).build()));

        assertThrows(ResponseStatusException.class,
                () -> service.deliver(request.getId(), course.getId(), login(teacherId, "TEACHER")));
        assertEquals("DRAFT", course.getStatus());
    }

    @Test
    void delivery_parentSeesCourseOnlyAfterStaffApproval() {
        setup();
        course.setCustomizeRequestId(request.getId());
        when(courses.findFirstByCustomizeRequestId(request.getId())).thenReturn(Optional.of(course));
        String parent = login(parentId, "PARENT");

        assertThrows(ResponseStatusException.class, () -> service.delivery(request.getId(), parent)); // còn FUNDED
        request.setStatus("DELIVERED");
        assertEquals("DELIVERED", service.delivery(request.getId(), parent).status());

        // Giáo viên thì xem được cả khi đang chờ duyệt.
        request.setStatus("FUNDED");
        assertEquals("FUNDED", service.delivery(request.getId(), login(teacherId, "TEACHER")).status());
    }

    @Test
    void historyIsVisibleToParentAndAcceptedTeacherOnly() {
        setup();
        var delivery = com.example.art_course.customize_request.entity.CustomizeDeliveryHistory.builder()
                .requestId(request.getId()).courseId(course.getId()).courseName(course.getName())
                .deliveredAt(java.time.Instant.now()).result("REJECTED").reason("Cần sửa bài").build();
        when(history.findAllByRequestIdOrderByDeliveredAtDesc(request.getId()))
                .thenReturn(List.of(delivery));

        assertEquals("Cần sửa bài", service.history(request.getId(), login(parentId, "PARENT")).getFirst().reason());
        assertEquals(1, service.history(request.getId(), login(teacherId, "TEACHER")).size());
        assertThrows(ResponseStatusException.class,
                () -> service.history(request.getId(), login(UUID.randomUUID(), "TEACHER")));
    }

    @Test
    void delivery_includesLessonPlaybackAndAssignmentDataBeforeAcceptance() {
        setup();
        request.setStatus("DELIVERED");
        course.setStatus("PUBLISHED");
        when(courses.findFirstByCustomizeRequestId(request.getId())).thenReturn(Optional.of(course));
        UUID lessonId = UUID.randomUUID();
        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of(Lesson.builder()
                .id(lessonId).title("Mèo ngồi").isTrial(false).hasAssignment(true)
                .videoStatus("READY").durationSeconds(300)
                .assignmentTitle("Vẽ mèo nhà em").assignmentPrompt("Vẽ một chú mèo đang ngồi.").build()));

        var lesson = service.delivery(request.getId(), login(parentId, "PARENT")).lessons().getFirst();

        assertEquals(lessonId, lesson.id());
        assertEquals("Mèo ngồi", lesson.title());
        assertFalse(lesson.trial());
        assertTrue(lesson.hasAssignment());
        assertEquals("READY", lesson.videoStatus());
        assertEquals(300, lesson.durationSeconds());
        assertEquals("Vẽ mèo nhà em", lesson.assignmentTitle());
        assertEquals("Vẽ một chú mèo đang ngồi.", lesson.assignmentPrompt());
    }

    @Test
    void delivery_parentOfACancelledButPaidRequestSeesItToAskForRefund() {
        setup();
        course.setCustomizeRequestId(request.getId());
        when(courses.findFirstByCustomizeRequestId(request.getId())).thenReturn(Optional.of(course));
        String parent = login(parentId, "PARENT");
        request.setStatus("CANCELLED");
        assertThrows(ResponseStatusException.class, () -> service.delivery(request.getId(), parent)); // chưa trả tiền

        Order paid = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        OrderDetail line = OrderDetail.builder().id(UUID.randomUUID()).orderId(paid.getId()).customizeRequestId(request.getId()).build();
        when(details.findByCustomizeRequestId(request.getId())).thenReturn(List.of(line));
        when(orders.findById(paid.getId())).thenReturn(Optional.of(paid));
        when(orders.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));

        var view = service.delivery(request.getId(), parent);

        assertEquals(paid.getId(), view.orderId());
        assertEquals(line.getId(), view.orderDetailId());
    }

    @Test
    void rejectDelivery_sendsReasonToTeacherByMessage_andReturnsRequestToFunded() {
        setup();
        var delivered = com.example.art_course.customize_request.entity.CustomizeDeliveryHistory.builder()
                .requestId(request.getId()).build();
        when(history.findFirstByRequestIdAndResolvedAtIsNullOrderByDeliveredAtDesc(request.getId()))
                .thenReturn(Optional.of(delivered));
        request.setStatus("DELIVERED");
        course.setStatus("PUBLISHED");
        course.setCustomizeRequestId(request.getId());
        when(courses.findFirstByCustomizeRequestId(request.getId())).thenReturn(Optional.of(course));
        Conversation conversation = Conversation.builder().id(UUID.randomUUID()).parentId(parentId).teacherId(teacherId).build();
        when(conversations.findFirstByContextTypeAndContextIdAndTeacherId("CUSTOMIZE_REQUEST", request.getId(), teacherId))
                .thenReturn(Optional.of(conversation));

        service.rejectDelivery(request.getId(), "Thiếu bài tô màu", login(parentId, "PARENT"));

        assertEquals("FUNDED", request.getStatus());
        assertEquals("REJECTED", delivered.getResult());
        assertEquals("Thiếu bài tô màu", delivered.getReason());
        assertNotNull(delivered.getResolvedAt());
        assertEquals("DRAFT", course.getStatus()); // giáo viên sửa rồi gửi Staff duyệt lại
        assertTrue(course.getReviewNote().contains("Thiếu bài tô màu"));
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(messages).save(message.capture());
        assertEquals(parentId, message.getValue().getSenderId());
        assertEquals(conversation.getId(), message.getValue().getConversationId());
        assertTrue(message.getValue().getContent().contains("Thiếu bài tô màu"));
        verify(notifications).notifyCustomizeDeliveryRejected(request.getId(), teacherId, "Vẽ mèo cho bé", "Thiếu bài tô màu");
    }

    @Test
    void rejectDelivery_requiresOwnerParent_deliveredRequest_andReason() {
        setup();
        String parent = login(parentId, "PARENT");
        // còn FUNDED
        assertThrows(ResponseStatusException.class, () -> service.rejectDelivery(request.getId(), "x", parent));

        request.setStatus("DELIVERED");
        assertThrows(ResponseStatusException.class, () -> service.rejectDelivery(request.getId(), " ", parent));
        String stranger = login(UUID.randomUUID(), "PARENT");
        assertThrows(ResponseStatusException.class, () -> service.rejectDelivery(request.getId(), "x", stranger));
        verify(messages, never()).save(any());
        assertEquals("DELIVERED", request.getStatus());
    }

    @Test
    void complete_grantsChildAccess_marksLineCompleted_andTriesEscrowRelease() {
        setup();
        var delivered = com.example.art_course.customize_request.entity.CustomizeDeliveryHistory.builder()
                .requestId(request.getId()).build();
        when(history.findFirstByRequestIdAndResolvedAtIsNullOrderByDeliveredAtDesc(request.getId()))
                .thenReturn(Optional.of(delivered));
        request.setStatus("DELIVERED");
        course.setCustomizeRequestId(request.getId());
        when(courses.findFirstByCustomizeRequestId(request.getId())).thenReturn(Optional.of(course));
        UUID childId = UUID.randomUUID();
        when(children.findByUserIdAndParentId(childId, parentId)).thenReturn(Optional.of(new Child()));
        Order paid = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        OrderDetail line = OrderDetail.builder().orderId(paid.getId()).customizeRequestId(request.getId()).build();
        when(details.findByCustomizeRequestId(request.getId())).thenReturn(List.of(line));
        when(orders.findById(paid.getId())).thenReturn(Optional.of(paid));
        when(orders.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));

        service.complete(request.getId(), childId, login(parentId, "PARENT"));

        assertEquals("COMPLETED", request.getStatus());
        assertEquals("CONFIRMED", delivered.getResult());
        assertNotNull(delivered.getResolvedAt());
        assertEquals(DeliveryStatus.COMPLETED, line.getDeliveryStatus());
        assertEquals(childId, line.getChildId());
        verify(enrollment).grantAccess(childId, course.getId(), paid.getId());
        verify(escrow).tryReleaseEscrow(paid.getId());
        verify(notifications).notifyCustomizeCompleted(request.getId(), teacherId, "Vẽ mèo cho bé");
    }

    @Test
    void completeWithoutChoosingAChildKeepsCourseReadyForLaterAssignment() {
        setup();
        request.setStatus("DELIVERED");
        course.setCustomizeRequestId(request.getId());
        when(courses.findFirstByCustomizeRequestId(request.getId())).thenReturn(Optional.of(course));
        Order paid = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        OrderDetail line = OrderDetail.builder().orderId(paid.getId()).customizeRequestId(request.getId()).build();
        when(details.findByCustomizeRequestId(request.getId())).thenReturn(List.of(line));
        when(orders.findById(paid.getId())).thenReturn(Optional.of(paid));
        when(orders.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));

        service.complete(request.getId(), null, login(parentId, "PARENT"));

        assertEquals("COMPLETED", request.getStatus());
        assertNull(line.getChildId());
        verify(enrollment, never()).grantAccess(any(), any(), any());
    }

    @Test
    void completeGrantsAllChildrenChosenBeforeDelivery() {
        setup();
        request.setStatus("DELIVERED");
        course.setCustomizeRequestId(request.getId());
        when(courses.findFirstByCustomizeRequestId(request.getId())).thenReturn(Optional.of(course));
        Order paid = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        OrderDetail line = OrderDetail.builder().orderId(paid.getId()).customizeRequestId(request.getId()).build();
        when(details.findByCustomizeRequestId(request.getId())).thenReturn(List.of(line));
        when(orders.findById(paid.getId())).thenReturn(Optional.of(paid));
        when(orders.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        ChildrenCourse a = ChildrenCourse.builder().orderId(paid.getId()).childId(first).build();
        ChildrenCourse b = ChildrenCourse.builder().orderId(paid.getId()).childId(second).build();
        ChildrenCourse c = ChildrenCourse.builder().orderId(paid.getId()).childId(third).build();
        when(childrenCourses.findByOrderId(paid.getId())).thenReturn(List.of(a, b, c));

        service.complete(request.getId(), null, login(parentId, "PARENT"));

        assertEquals(course.getId(), a.getCourseId());
        assertEquals(course.getId(), b.getCourseId());
        assertEquals(course.getId(), c.getCourseId());
        verify(enrollment).grantAccess(first, course.getId(), paid.getId());
        verify(enrollment).grantAccess(second, course.getId(), paid.getId());
        verify(enrollment).grantAccess(third, course.getId(), paid.getId());
    }

    @Test
    void complete_rejectsOtherParentsChild_andNotDeliveredRequest() {
        setup();
        String parent = login(parentId, "PARENT");
        assertThrows(ResponseStatusException.class,
                () -> service.complete(request.getId(), UUID.randomUUID(), parent)); // còn FUNDED

        request.setStatus("DELIVERED");
        UUID strangerChild = UUID.randomUUID();
        when(children.findByUserIdAndParentId(strangerChild, parentId)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.complete(request.getId(), strangerChild, parent));
        verify(enrollment, never()).grantAccess(any(), any(), any());
        assertEquals("DELIVERED", request.getStatus());
    }
}
