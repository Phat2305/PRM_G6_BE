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
import com.example.art_course.modules.finance.service.EscrowReleaseService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.offer.entity.Offer;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CUS-11/12: bàn giao (FUNDED → DELIVERED) và xác nhận hoàn thành (DELIVERED → COMPLETED).
class CustomizeDeliveryTest {

    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final IEnrollmentContract enrollment = mock(IEnrollmentContract.class);
    private final EscrowReleaseService escrow = mock(EscrowReleaseService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CustomizeDeliveryService service = new CustomizeDeliveryService(requests, offers, courses,
            lessons, children, details, orders, enrollment, escrow, notifications, users, mock(TeacherNames.class));

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

    private void setup() {
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
        when(offers.findAllByRequestIdAndTeacherId(request.getId(), teacherId))
                .thenReturn(List.of(Offer.builder().teacherId(teacherId).status("accepted").build()));
        when(lessons.findAllByCourseId(course.getId()))
                .thenReturn(List.of(Lesson.builder().title("Bài 1").isTrial(true).hasAssignment(false).build()));
    }

    @Test
    void deliver_linksCourse_publishesIt_andMovesRequestToDelivered() {
        setup();
        var view = service.deliver(request.getId(), course.getId(), login(teacherId, "TEACHER"));

        assertEquals("DELIVERED", request.getStatus());
        assertEquals(request.getId(), course.getCustomizeRequestId());
        assertEquals("PUBLISHED", course.getStatus());
        assertEquals(1, view.lessons().size());
        verify(notifications).notifyCustomizeDelivered(request.getId(), parentId, "Vẽ mèo cho bé");
    }

    @Test
    void deliver_rejectsOtherTeacher_unpaidRequest_andEmptyCourse() {
        setup();
        String other = login(UUID.randomUUID(), "TEACHER");
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), other));

        String teacher = login(teacherId, "TEACHER");
        request.setStatus("ACCEPTED");
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), teacher));

        request.setStatus("FUNDED");
        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.deliver(request.getId(), course.getId(), teacher));
        assertEquals("FUNDED", request.getStatus());
    }

    @Test
    void complete_grantsChildAccess_marksLineCompleted_andTriesEscrowRelease() {
        setup();
        request.setStatus("DELIVERED");
        course.setCustomizeRequestId(request.getId());
        when(courses.findFirstByCustomizeRequestId(request.getId())).thenReturn(Optional.of(course));
        UUID childId = UUID.randomUUID();
        when(children.findByUserIdAndParentId(childId, parentId)).thenReturn(Optional.of(new Child()));
        Order paid = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        OrderDetail line = OrderDetail.builder().orderId(paid.getId()).customizeRequestId(request.getId()).build();
        when(details.findByCustomizeRequestId(request.getId())).thenReturn(List.of(line));
        when(orders.findById(paid.getId())).thenReturn(Optional.of(paid));

        service.complete(request.getId(), childId, login(parentId, "PARENT"));

        assertEquals("COMPLETED", request.getStatus());
        assertEquals(DeliveryStatus.COMPLETED, line.getDeliveryStatus());
        assertEquals(childId, line.getChildId());
        verify(enrollment).grantAccess(childId, course.getId(), paid.getId());
        verify(escrow).tryReleaseEscrow(paid.getId());
        verify(notifications).notifyCustomizeCompleted(request.getId(), teacherId, "Vẽ mèo cho bé");
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
