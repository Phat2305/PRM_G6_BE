package com.example.art_course.common;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.modules.cart.repository.*;
import com.example.art_course.modules.order.entity.*;
import com.example.art_course.modules.order.repository.*;
import com.example.art_course.modules.order.service.OrderService;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// SHOP-03/04/05, PAY-08: gọi service thật, không dựng lại thuật toán trong test.
class OrderServiceSafetyTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final ChildrenCourseRepository childrenCourses = mock(ChildrenCourseRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final SupplyRepository supplies = mock(SupplyRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final ConfigService config = mock(ConfigService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final IEnrollmentContract enrollment = mock(IEnrollmentContract.class);
    private final TeacherNames names = mock(TeacherNames.class);
    private final OrderService service = new OrderService(orders, details, childrenCourses, mock(CartRepository.class),
            mock(CartItemRepository.class), courses, supplies, entityManager, names, children,
            enrollment, config);
    private final UUID parentId = UUID.randomUUID();
    private Course first;
    private Course second;
    private Supply supply;

    @BeforeEach
    void setUp() {
        first = Course.builder().id(UUID.randomUUID()).teacherId(UUID.randomUUID()).name("Khóa A")
                .price(new BigDecimal("300000")).status("PUBLISHED").build();
        second = Course.builder().id(UUID.randomUUID()).teacherId(UUID.randomUUID()).name("Khóa B")
                .price(new BigDecimal("500000")).status("PUBLISHED").build();
        supply = Supply.builder().id(UUID.randomUUID()).teacherId(first.getTeacherId()).name("Bộ màu")
                .price(new BigDecimal("120000")).stockQuantity(5).reservedQuantity(0).soldQuantity(0).status("ACTIVE").build();
        when(courses.findById(first.getId())).thenReturn(Optional.of(first));
        when(courses.findById(second.getId())).thenReturn(Optional.of(second));
        when(supplies.findByIdForUpdate(supply.getId())).thenReturn(Optional.of(supply));
        when(config.getCommissionRateCourse()).thenReturn(new BigDecimal("0.20"));
        when(config.getCommissionRateSupply()).thenReturn(new BigDecimal("0.10"));
        when(config.getOrderPaymentTimeoutMinutes()).thenReturn(15);
        when(config.getDefaultShippingFee()).thenReturn(Money.of(30000));
        when(names.shippingFeeOf(any())).thenReturn(null);
        when(names.byIds(any())).thenReturn(Map.of(first.getTeacherId(), "Cô A", second.getTeacherId(), "Cô B"));
        when(orders.save(any())).thenAnswer(i -> {
            Order order = i.getArgument(0);
            if (order.getId() == null) order.setId(UUID.randomUUID());
            return order;
        });
    }

    private OrderService.CheckoutItem course(Course c) {
        return new OrderService.CheckoutItem("COURSE", c.getId(), null, null, 1);
    }

    private OrderService.CheckoutRequest request() {
        return new OrderService.CheckoutRequest(false,
                List.of(course(first), new OrderService.CheckoutItem("SUPPLY", null, supply.getId(), null, 1), course(second)),
                " Chị Mai ", "0901 234 567", " 12 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh ");
    }

    @Test
    void splitsActualCheckoutByTeacherAndSavesRecipientSnapshotOnlyOnPhysicalOrder() {
        var request = request();
        var result = service.createOrdersFromCheckout(parentId, request).stream()
                .collect(Collectors.toMap(Order::getTeacherId, o -> o));
        assertEquals(2, result.size());
        Order physical = result.get(first.getTeacherId());
        assertEquals(new BigDecimal("450000"), physical.getTotalAmount());
        assertEquals(new BigDecimal("30000"), physical.getShippingFee());
        assertEquals("Chị Mai", physical.getRecipientName());
        assertEquals("0901234567", physical.getRecipientPhone());
        request.setShippingAddress("Địa chỉ mới trong hồ sơ");
        assertEquals("12 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh", physical.getShippingAddress());
        Order digital = result.get(second.getTeacherId());
        assertEquals(new BigDecimal("500000"), digital.getTotalAmount());
        assertNull(digital.getShippingAddress());
        assertEquals(1, supply.getReservedQuantity());
        verify(details, times(3)).save(any());
    }

    @Test
    void physicalCheckoutRejectsMissingOrInvalidRecipient() {
        var request = request();
        request.setRecipientPhone("123");
        assertEquals("INVALID_RECIPIENT", assertThrows(AppError.class,
                () -> service.createOrdersFromCheckout(parentId, request)).getErrorCode());
        verify(orders, never()).save(any());
    }

    @Test
    void checkoutRejectsAnotherParentsChild() {
        var item = course(first);
        item.setChildId(UUID.randomUUID());
        var request = new OrderService.CheckoutRequest(false, List.of(item), null, null, null);
        assertEquals("CHILD_NOT_FOUND", assertThrows(AppError.class,
                () -> service.createOrdersFromCheckout(parentId, request)).getErrorCode());
        verify(children).findByUserIdAndParentId(item.getChildId(), parentId);
        verify(orders, never()).save(any());
    }

    @Test
    void paidCourseCanAssignMoreThanTwoChildrenWithOnePurchase() {
        Order paid = Order.builder().id(UUID.randomUUID()).parentId(parentId).status(OrderStatus.PAID).build();
        OrderDetail line = OrderDetail.builder().id(UUID.randomUUID()).orderId(paid.getId())
                .courseId(first.getId()).unitPrice(first.getPrice()).build();
        UUID childA = UUID.randomUUID();
        UUID childB = UUID.randomUUID();
        UUID childC = UUID.randomUUID();
        when(orders.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));
        when(details.findById(line.getId())).thenReturn(Optional.of(line));
        when(children.findByUserIdAndParentId(any(), eq(parentId))).thenReturn(Optional.of(new Child()));
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        ChildrenCourse firstAssignment = ChildrenCourse.builder().childId(childA).build();
        ChildrenCourse secondAssignment = ChildrenCourse.builder().childId(childB).build();
        ChildrenCourse thirdAssignment = ChildrenCourse.builder().childId(childC).build();
        when(childrenCourses.findByOrderIdAndCourseId(paid.getId(), first.getId()))
                .thenReturn(List.of(), List.of(firstAssignment),
                        List.of(firstAssignment, secondAssignment),
                        List.of(firstAssignment, secondAssignment, thirdAssignment));

        service.assignChild(paid.getId(), line.getId(), parentId, childA);
        service.assignChild(paid.getId(), line.getId(), parentId, childB);
        service.assignChild(paid.getId(), line.getId(), parentId, childC);
        service.assignChild(paid.getId(), line.getId(), parentId, childA);
        var saved = org.mockito.ArgumentCaptor.forClass(ChildrenCourse.class);
        verify(childrenCourses, times(3)).save(saved.capture());
        assertEquals(first.getPrice(), saved.getAllValues().get(0).getPrice());
        assertEquals(BigDecimal.ZERO, saved.getAllValues().get(1).getPrice());
        assertEquals(BigDecimal.ZERO, saved.getAllValues().get(2).getPrice());
        verify(enrollment).grantAccess(childA, first.getId(), paid.getId());
        verify(enrollment).grantAccess(childB, first.getId(), paid.getId());
        verify(enrollment).grantAccess(childC, first.getId(), paid.getId());
    }

    @Test
    void paidCustomizeCanAssignThirdChildBeforeDelivery() {
        Order paid = Order.builder().id(UUID.randomUUID()).parentId(parentId).status(OrderStatus.PAID).build();
        UUID firstChild = UUID.randomUUID();
        UUID secondChild = UUID.randomUUID();
        UUID thirdChild = UUID.randomUUID();
        OrderDetail line = OrderDetail.builder().id(UUID.randomUUID()).orderId(paid.getId())
                .customizeRequestId(UUID.randomUUID()).childId(firstChild)
                .unitPrice(new BigDecimal("400000")).build();
        when(orders.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));
        when(details.findById(line.getId())).thenReturn(Optional.of(line));
        when(children.findByUserIdAndParentId(thirdChild, parentId)).thenReturn(Optional.of(new Child()));
        when(childrenCourses.findByOrderId(paid.getId())).thenReturn(List.of(
                ChildrenCourse.builder().orderId(paid.getId()).childId(firstChild).build(),
                ChildrenCourse.builder().orderId(paid.getId()).childId(secondChild).build()));

        service.assignChild(paid.getId(), line.getId(), parentId, thirdChild);

        var saved = org.mockito.ArgumentCaptor.forClass(ChildrenCourse.class);
        verify(childrenCourses).save(saved.capture());
        assertEquals(thirdChild, saved.getValue().getChildId());
        assertNull(saved.getValue().getCourseId());
        assertEquals(BigDecimal.ZERO, saved.getValue().getPrice());
        verify(enrollment, never()).grantAccess(any(), any(), any());
    }

    @Test
    void expirationDoesNotOverwriteAnOrderPaidAfterCandidateSelection() {
        Order paid = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID)
                .expiredAt(Instant.now().minusSeconds(60)).build();
        when(orders.findExpiredIds(eq(OrderStatus.PENDING_PAYMENT), any())).thenReturn(List.of(paid.getId()));
        when(orders.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));
        assertEquals(0, service.expirePendingOrders());
        assertEquals(OrderStatus.PAID, paid.getStatus());
        verify(orders, never()).save(any());
        verifyNoInteractions(details);
    }

    @Test
    void teacherOrdersLoadParentProfileNamesInOneBatch() {
        UUID teacherId = first.getTeacherId();
        UUID otherParent = UUID.randomUUID();
        Order a = Order.builder().parentId(parentId).teacherId(teacherId).build();
        Order b = Order.builder().parentId(parentId).teacherId(teacherId).build();
        Order c = Order.builder().parentId(otherParent).teacherId(teacherId).build();
        when(orders.findByTeacherIdOrderByCreatedAtDesc(teacherId)).thenReturn(List.of(a, b, c));
        when(names.parentsByIds(List.of(parentId, otherParent))).thenReturn(Map.of(parentId, "Chị Mai"));

        var result = service.getTeacherOrders(teacherId);

        assertEquals("Chị Mai", result.get(0).getParentName());
        assertEquals("Chị Mai", result.get(1).getParentName());
        assertNull(result.get(2).getParentName());
        verify(names).parentsByIds(List.of(parentId, otherParent));
    }

    @Test
    void orderDetailReturnsParentProfileNameSeparatelyFromRecipient() {
        Order order = Order.builder().id(UUID.randomUUID()).parentId(parentId)
                .teacherId(first.getTeacherId()).recipientName("Người nhận khác").build();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(names.parentsByIds(List.of(parentId))).thenReturn(Map.of(parentId, "Chị Mai"));

        var result = service.getOrderDetail(order.getId(), first.getTeacherId(), "TEACHER");

        assertEquals("Chị Mai", result.order().getParentName());
        assertEquals("Người nhận khác", result.order().getRecipientName());
        assertThrows(AppError.class, () -> service.getOrderDetail(order.getId(), UUID.randomUUID(), "TEACHER"));
    }

    @Test
    void repeatedCancellationOnlyReleasesReservationOnce() {
        Order pending = Order.builder().id(UUID.randomUUID()).parentId(parentId).status(OrderStatus.PENDING_PAYMENT).build();
        supply.setReservedQuantity(1);
        when(orders.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(details.findByOrderIdAndSupplyIdIsNotNull(pending.getId())).thenReturn(List.of(
                OrderDetail.builder().orderId(pending.getId()).supplyId(supply.getId()).quantity(1).build()));
        service.cancelOrder(pending.getId(), parentId, "PARENT");
        assertEquals(0, supply.getReservedQuantity());
        assertEquals("CANNOT_CANCEL", assertThrows(AppError.class,
                () -> service.cancelOrder(pending.getId(), parentId, "PARENT")).getErrorCode());
        verify(supplies, times(1)).save(supply);
    }
}
