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

    // Khách trả lần lượt từng QR nên hạn chung nhân theo số đơn: 2 giáo viên = 2 x 15 phút, 1 giáo viên = 15 phút.
    @Test
    void paymentDeadlineScalesWithTheNumberOfOrdersInOneCheckout() {
        Instant before = Instant.now();
        var two = service.createOrdersFromCheckout(parentId, request());
        two.forEach(o -> {
            long minutes = java.time.Duration.between(before, o.getExpiredAt()).toMinutes();
            assertTrue(minutes >= 29 && minutes <= 30, "hai đơn: " + minutes);
        });

        var single = service.createOrdersFromCheckout(parentId, new OrderService.CheckoutRequest(false,
                List.of(course(first)), null, null, null));
        long one = java.time.Duration.between(before, single.get(0).getExpiredAt()).toMinutes();
        assertTrue(one >= 14 && one <= 15, "một đơn: " + one);
    }

    @Test
    void deadlineMultiplierIsCappedSoABigCartDoesNotHoldStockForever() {
        List<OrderService.CheckoutItem> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Course c = Course.builder().id(UUID.randomUUID()).teacherId(UUID.randomUUID()).name("K" + i)
                    .price(new BigDecimal("100000")).status("PUBLISHED").build();
            when(courses.findById(c.getId())).thenReturn(Optional.of(c));
            items.add(course(c));
        }
        Instant before = Instant.now();

        var orders8 = service.createOrdersFromCheckout(parentId,
                new OrderService.CheckoutRequest(false, items, null, null, null));

        assertEquals(8, orders8.size());
        long minutes = java.time.Duration.between(before, orders8.get(0).getExpiredAt()).toMinutes();
        assertTrue(minutes >= 74 && minutes <= 75, "tối đa 5 x 15 phút: " + minutes);
    }

    // Đặt lại món đang ở đơn chờ thanh toán: đơn cũ bị hủy và trả tồn kho giữ chỗ, không có hai đơn cùng chờ.
    @Test
    void checkoutAgainCancelsAnOlderPendingOrderWithTheSameItems() {
        Order old = Order.builder().id(UUID.randomUUID()).parentId(parentId).teacherId(first.getTeacherId())
                .status(OrderStatus.PENDING_PAYMENT).totalAmount(new BigDecimal("150000")).build();
        OrderDetail oldCourse = OrderDetail.builder().id(UUID.randomUUID()).orderId(old.getId())
                .courseId(first.getId()).quantity(1).build();
        OrderDetail oldSupply = OrderDetail.builder().id(UUID.randomUUID()).orderId(old.getId())
                .supplyId(supply.getId()).quantity(2).build();
        supply.setReservedQuantity(2);
        when(orders.findByParentIdAndStatus(parentId, OrderStatus.PENDING_PAYMENT)).thenReturn(List.of(old));
        when(orders.findByIdForUpdate(old.getId())).thenReturn(Optional.of(old));
        when(details.findByOrderId(old.getId())).thenReturn(List.of(oldCourse, oldSupply));
        when(details.findByOrderIdAndSupplyIdIsNotNull(old.getId())).thenReturn(List.of(oldSupply));

        service.createOrdersFromCheckout(parentId, request());

        assertEquals(OrderStatus.CANCELLED, old.getStatus());
        // 2 giữ chỗ của đơn cũ được trả, đơn mới giữ 1.
        assertEquals(1, supply.getReservedQuantity());
    }

    @Test
    void unrelatedOrCustomizeOrdersAreLeftAlone() {
        Order other = Order.builder().id(UUID.randomUUID()).parentId(parentId).teacherId(UUID.randomUUID())
                .status(OrderStatus.PENDING_PAYMENT).totalAmount(BigDecimal.TEN).build();
        Order customize = Order.builder().id(UUID.randomUUID()).parentId(parentId).teacherId(first.getTeacherId())
                .status(OrderStatus.PENDING_PAYMENT).totalAmount(BigDecimal.TEN).build();
        when(orders.findByParentIdAndStatus(parentId, OrderStatus.PENDING_PAYMENT)).thenReturn(List.of(other, customize));
        when(details.findByOrderId(other.getId())).thenReturn(List.of(
                OrderDetail.builder().orderId(other.getId()).courseId(UUID.randomUUID()).build()));
        when(details.findByOrderId(customize.getId())).thenReturn(List.of(
                OrderDetail.builder().orderId(customize.getId()).courseId(first.getId())
                        .customizeRequestId(UUID.randomUUID()).build()));

        service.createOrdersFromCheckout(parentId, request());

        assertEquals(OrderStatus.PENDING_PAYMENT, other.getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, customize.getStatus());
    }

    // SHOP-04: khóa mua một lần theo phụ huynh, nên đặt lại cho bé khác cũng hủy đơn chờ cũ của khóa đó.
    @Test
    void courseForAnotherChildCancelsTheOlderPendingOrderOfTheSameCourse() {
        UUID childA = UUID.randomUUID();
        UUID childB = UUID.randomUUID();
        Order old = Order.builder().id(UUID.randomUUID()).parentId(parentId).teacherId(first.getTeacherId())
                .status(OrderStatus.PENDING_PAYMENT).totalAmount(BigDecimal.TEN).build();
        when(orders.findByParentIdAndStatus(parentId, OrderStatus.PENDING_PAYMENT)).thenReturn(List.of(old));
        when(orders.findByIdForUpdate(old.getId())).thenReturn(Optional.of(old));
        when(details.findByOrderId(old.getId())).thenReturn(List.of(
                OrderDetail.builder().orderId(old.getId()).courseId(first.getId()).childId(childA).build()));
        when(children.findByUserIdAndParentId(childB, parentId)).thenReturn(Optional.of(new Child()));
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        service.createOrdersFromCheckout(parentId, new OrderService.CheckoutRequest(false,
                List.of(new OrderService.CheckoutItem("COURSE", first.getId(), null, childB, 1)), null, null, null));

        assertEquals(OrderStatus.CANCELLED, old.getStatus());
    }

    // SHOP-04: phụ huynh đã trả tiền khóa (kể cả chưa gán bé) thì không tạo đơn mới.
    @Test
    void checkoutRejectsACourseTheParentAlreadyPaidFor() {
        when(details.parentOwnsCourse(parentId, first.getId())).thenReturn(true);

        var request = new OrderService.CheckoutRequest(false, List.of(course(first)), null, null, null);

        assertEquals("COURSE_ALREADY_OWNED", assertThrows(AppError.class,
                () -> service.createOrdersFromCheckout(parentId, request)).getErrorCode());
        verify(orders, never()).save(any());
    }

    // CUS-10: khóa làm riêng cho một yêu cầu không đặt qua checkout thường (đi qua /orders/customize).
    @Test
    void checkoutRejectsACustomizeCourse() {
        first.setCustomizeRequestId(UUID.randomUUID());

        var request = new OrderService.CheckoutRequest(false, List.of(course(first)), null, null, null);

        assertEquals("COURSE_UNAVAILABLE", assertThrows(AppError.class,
                () -> service.createOrdersFromCheckout(parentId, request)).getErrorCode());
        verify(orders, never()).save(any());
    }

    @Test
    void checkoutRejectsTheSameCourseTwiceInOneRequest() {
        var request = new OrderService.CheckoutRequest(false, List.of(course(first), course(first)), null, null, null);

        assertEquals("DUPLICATE_COURSE_IN_ORDER", assertThrows(AppError.class,
                () -> service.createOrdersFromCheckout(parentId, request)).getErrorCode());
        verify(orders, never()).save(any());
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
