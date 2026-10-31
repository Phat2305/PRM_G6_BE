package com.example.art_course.modules.order.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SHOP-04: trang "Khóa học của tôi" của phụ huynh. Một thẻ cho mỗi khóa đã trả tiền (đơn PAID, chưa hoàn tiền),
 * kèm các bé đang được gán và tiến độ. Khóa đã hoàn tiền không hiện. Cùng điều kiện sở hữu với
 * OrderDetailRepository.parentOwnsCourse nên nút mua và trang này không mâu thuẫn nhau.
 */
@Service
@RequiredArgsConstructor
public class OwnedCourseService {

    private static final Set<String> VISIBLE_CUSTOMIZE = Set.of("DELIVERED", "COMPLETED");

    private final OrderDetailRepository orderDetailRepository;
    private final OrderRepository orderRepository;
    private final ChildrenCourseRepository childrenCourseRepository;
    private final CourseRepository courseRepository;
    private final CustomizeRequestRepository customizeRequestRepository;
    private final LessonRepository lessonRepository;
    private final ChildProgressRepository childProgressRepository;
    private final ChildRepository childRepository;
    private final TeacherNames teacherNames;

    public record ChildProgressView(UUID childId, String displayName, int progressPercent) {}

    // kind: NORMAL hoặc CUSTOMIZE; customizeStatus chỉ có ở khóa customize (DELIVERED = chờ phụ huynh nhận khóa).
    public record OwnedCourseView(UUID courseId, String name, String coverImageUrl, String teacherName, String kind,
                                  UUID orderId, UUID orderDetailId, Instant purchasedAt, boolean needsAssign,
                                  UUID customizeRequestId, String customizeStatus, List<ChildProgressView> children) {}

    // Một dòng đơn đã gắn với khóa của nó; khóa customize được tra qua yêu cầu.
    private record Owned(OrderDetail line, Order order, Course course, CustomizeRequest request) {}

    @Transactional(readOnly = true)
    public List<OwnedCourseView> list(UUID parentId) {
        List<OrderDetail> lines = orderDetailRepository.findOwnedCourseLines(parentId);
        if (lines.isEmpty()) return List.of();

        Map<UUID, Order> orders = orderRepository.findAllById(
                lines.stream().map(OrderDetail::getOrderId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<UUID, Course> byId = courseRepository.findAllById(
                lines.stream().map(OrderDetail::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Course::getId, Function.identity()));
        Set<UUID> requestIds = lines.stream().map(OrderDetail::getCustomizeRequestId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Course> byRequest = requestIds.isEmpty() ? Map.of() : courseRepository
                .findAllByCustomizeRequestIdIn(requestIds).stream()
                .collect(Collectors.toMap(Course::getCustomizeRequestId, Function.identity(), (a, b) -> a));
        Map<UUID, CustomizeRequest> requests = customizeRequestRepository.findAllById(requestIds).stream()
                .collect(Collectors.toMap(CustomizeRequest::getId, Function.identity()));

        // Mua sớm nhất đứng trước; một khóa mua nhiều lần (trước khi có quy tắc chặn trùng) chỉ hiện một thẻ.
        Map<UUID, List<Owned>> byCourse = new LinkedHashMap<>();
        lines.stream()
                .filter(l -> orders.containsKey(l.getOrderId()))
                .sorted(Comparator.comparing((OrderDetail l) -> orders.get(l.getOrderId()).getCreatedAt()))
                .forEach(line -> {
                    Order order = orders.get(line.getOrderId());
                    CustomizeRequest request = line.getCustomizeRequestId() == null
                            ? null : requests.get(line.getCustomizeRequestId());
                    Course course = request == null ? byId.get(line.getCourseId()) : byRequest.get(request.getId());
                    // Phụ huynh chỉ thấy khóa riêng sau khi Staff duyệt bàn giao (cùng quy tắc với CustomizeDeliveryService).
                    if (course == null || (line.getCustomizeRequestId() != null
                            && (request == null || !VISIBLE_CUSTOMIZE.contains(request.getStatus().toUpperCase())))) {
                        return;
                    }
                    byCourse.computeIfAbsent(course.getId(), k -> new ArrayList<>())
                            .add(new Owned(line, order, course, request));
                });
        if (byCourse.isEmpty()) return List.of();

        Map<UUID, Set<UUID>> childIdsByCourse = assignedChildren(byCourse);
        Set<UUID> allChildIds = childIdsByCourse.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Map<UUID, String> childNames = allChildIds.isEmpty() ? Map.of() : childRepository.findAllById(allChildIds)
                .stream().collect(Collectors.toMap(Child::getUserId, Child::getDisplayName));
        Map<UUID, Integer> totalLessons = totalLessons(byCourse.keySet());
        Map<String, Integer> doneLessons = allChildIds.isEmpty() ? Map.of() : doneLessons(byCourse.keySet(), allChildIds);
        Map<UUID, String> teachers = teacherNames.byIds(byCourse.values().stream()
                .map(l -> l.get(0).course().getTeacherId()).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<OwnedCourseView> views = new ArrayList<>();
        byCourse.forEach((courseId, owned) -> {
            Owned first = owned.get(0);
            Course course = first.course();
            int total = totalLessons.getOrDefault(courseId, 0);
            List<ChildProgressView> children = childIdsByCourse.get(courseId).stream()
                    .map(childId -> new ChildProgressView(childId, childNames.getOrDefault(childId, ""),
                            total == 0 ? 0 : Math.min(100, doneLessons.getOrDefault(courseId + ":" + childId, 0) * 100 / total)))
                    .sorted(Comparator.comparing(ChildProgressView::displayName))
                    .toList();
            CustomizeRequest request = first.request();
            views.add(new OwnedCourseView(courseId, course.getName(), course.getCoverImageUrl(),
                    teachers.get(course.getTeacherId()), request == null ? "NORMAL" : "CUSTOMIZE",
                    first.order().getId(), first.line().getId(), first.order().getCreatedAt(), children.isEmpty(),
                    request == null ? null : request.getId(), request == null ? null : request.getStatus(), children));
        });
        return views;
    }

    // Bé được gán qua mọi đơn của khóa. Khóa thường tra theo (đơn, khóa); khóa customize theo đơn vì course_id chỉ
    // được ghi sau khi phụ huynh nhận khóa.
    private Map<UUID, Set<UUID>> assignedChildren(Map<UUID, List<Owned>> byCourse) {
        List<ChildrenCourse> rows = childrenCourseRepository.findByOrderIdIn(byCourse.values().stream()
                .flatMap(List::stream).map(o -> o.order().getId()).collect(Collectors.toSet()));
        Map<UUID, Set<UUID>> result = new HashMap<>();
        byCourse.forEach((courseId, owned) -> {
            Set<UUID> orderIds = owned.stream().map(o -> o.order().getId()).collect(Collectors.toSet());
            boolean customize = owned.get(0).request() != null;
            result.put(courseId, rows.stream()
                    .filter(r -> orderIds.contains(r.getOrderId()) && (customize || courseId.equals(r.getCourseId())))
                    .map(ChildrenCourse::getChildId)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        });
        return result;
    }

    private Map<UUID, Integer> totalLessons(Collection<UUID> courseIds) {
        Map<UUID, Integer> totals = new HashMap<>();
        for (Object[] row : lessonRepository.countByCourseIds(courseIds)) {
            totals.put(UUID.fromString(String.valueOf(row[0])), ((Number) row[1]).intValue());
        }
        return totals;
    }

    // Khóa "khóa:bé" -> số bài đã xong.
    private Map<String, Integer> doneLessons(Collection<UUID> courseIds, Collection<UUID> childIds) {
        Map<String, Integer> done = new HashMap<>();
        for (Object[] row : childProgressRepository.countCompletedByCourseAndChild(courseIds, childIds)) {
            done.put(row[0] + ":" + row[1], ((Number) row[2]).intValue());
        }
        return done;
    }
}
