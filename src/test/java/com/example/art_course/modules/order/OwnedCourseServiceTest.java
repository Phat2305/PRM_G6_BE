package com.example.art_course.modules.order;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.child_progress.repository.ChildProgressRepository;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.modules.order.entity.ChildrenCourse;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.ChildrenCourseRepository;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.order.service.OwnedCourseService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// SHOP-04: trang "Khóa học của tôi". Các repository là mock; điều kiện PAID / chưa hoàn tiền nằm trong câu SQL
// findOwnedCourseLines nên cần Testcontainers để kiểm, ở đây chỉ kiểm cách gom và gắn bé.
class OwnedCourseServiceTest {
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final ChildrenCourseRepository assignments = mock(ChildrenCourseRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final ChildProgressRepository progress = mock(ChildProgressRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final TeacherNames names = mock(TeacherNames.class);
    private final OwnedCourseService service = new OwnedCourseService(
            details, orders, assignments, courses, requests, lessons, progress, children, names);

    private final UUID parentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final Course course = Course.builder().id(UUID.randomUUID()).teacherId(teacherId).name("Màu nước")
            .coverImageUrl("/api/images/courses/x").status("PUBLISHED").build();

    private Order order(Instant createdAt) {
        return Order.builder().id(UUID.randomUUID()).parentId(parentId).createdAt(createdAt).build();
    }

    private OrderDetail courseLine(Order order) {
        return OrderDetail.builder().id(UUID.randomUUID()).orderId(order.getId()).courseId(course.getId()).build();
    }

    private void stubCommon(List<OrderDetail> lines, Order... placed) {
        when(details.findOwnedCourseLines(parentId)).thenReturn(lines);
        when(orders.findAllById(any())).thenReturn(List.of(placed));
        when(courses.findAllById(any())).thenReturn(List.of(course));
        when(names.byIds(any())).thenReturn(Map.of(teacherId, "Cô Mai"));
    }

    @Test
    void paidCourseWithoutChildShowsAsNeedingAssignment() {
        Order order = order(Instant.now());
        OrderDetail line = courseLine(order);
        stubCommon(List.of(line), order);

        var result = service.list(parentId);

        assertEquals(1, result.size());
        var view = result.get(0);
        assertEquals(course.getId(), view.courseId());
        assertEquals("Cô Mai", view.teacherName());
        assertEquals("NORMAL", view.kind());
        assertEquals(order.getId(), view.orderId());
        assertEquals(line.getId(), view.orderDetailId());
        assertTrue(view.needsAssign());
        assertTrue(view.children().isEmpty());
    }

    @Test
    void assignedChildShowsProgressAsPercentOfLessons() {
        Order order = order(Instant.now());
        UUID childId = UUID.randomUUID();
        stubCommon(List.of(courseLine(order)), order);
        when(assignments.findByOrderIdIn(any())).thenReturn(List.of(
                ChildrenCourse.builder().orderId(order.getId()).courseId(course.getId()).childId(childId).build()));
        when(children.findAllById(any())).thenReturn(List.of(Child.builder().userId(childId).displayName("Bin").build()));
        when(lessons.countByCourseIds(any())).thenReturn(rows(new Object[]{course.getId(), 4L}));
        when(progress.countCompletedByCourseAndChild(any(), any()))
                .thenReturn(rows(new Object[]{course.getId(), childId, 1L}));

        var view = service.list(parentId).get(0);

        assertFalse(view.needsAssign());
        assertEquals(1, view.children().size());
        assertEquals("Bin", view.children().get(0).displayName());
        assertEquals(25, view.children().get(0).progressPercent());
    }

    @Test
    void sameCourseBoughtTwiceIsOneCardWithChildrenFromBothOrders() {
        Order first = order(Instant.now().minusSeconds(60));
        Order second = order(Instant.now());
        UUID childA = UUID.randomUUID();
        UUID childB = UUID.randomUUID();
        OrderDetail firstLine = courseLine(first);
        stubCommon(List.of(courseLine(second), firstLine), first, second);
        when(assignments.findByOrderIdIn(any())).thenReturn(List.of(
                ChildrenCourse.builder().orderId(first.getId()).courseId(course.getId()).childId(childA).build(),
                ChildrenCourse.builder().orderId(second.getId()).courseId(course.getId()).childId(childB).build()));
        when(children.findAllById(any())).thenReturn(List.of(
                Child.builder().userId(childA).displayName("An").build(),
                Child.builder().userId(childB).displayName("Bin").build()));

        var result = service.list(parentId);

        assertEquals(1, result.size());
        assertEquals(first.getId(), result.get(0).orderId());
        assertEquals(firstLine.getId(), result.get(0).orderDetailId());
        assertEquals(List.of("An", "Bin"), result.get(0).children().stream().map(c -> c.displayName()).toList());
    }

    @Test
    void customizeCourseShowsOnlyAfterStaffDelivered() {
        Order order = order(Instant.now());
        UUID requestId = UUID.randomUUID();
        course.setCustomizeRequestId(requestId);
        OrderDetail line = OrderDetail.builder().id(UUID.randomUUID()).orderId(order.getId())
                .customizeRequestId(requestId).build();
        stubCommon(List.of(line), order);
        when(courses.findAllByCustomizeRequestIdIn(any())).thenReturn(List.of(course));
        CustomizeRequest request = CustomizeRequest.builder().id(requestId).status("FUNDED").build();
        when(requests.findAllById(any())).thenReturn(List.of(request));

        assertTrue(service.list(parentId).isEmpty(), "đang làm, phụ huynh chưa thấy");

        request.setStatus("DELIVERED");
        var view = service.list(parentId).get(0);
        assertEquals("CUSTOMIZE", view.kind());
        assertEquals("DELIVERED", view.customizeStatus());
        assertEquals(requestId, view.customizeRequestId());
        assertEquals(course.getId(), view.courseId());
    }

    @Test
    void noPaidLinesGivesEmptyList() {
        when(details.findOwnedCourseLines(parentId)).thenReturn(List.of());

        assertTrue(service.list(parentId).isEmpty());
    }

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }
}
