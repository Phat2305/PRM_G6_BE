package com.example.art_course.common;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.modules.cart.repository.*;
import com.example.art_course.modules.order.entity.*;
import com.example.art_course.modules.order.repository.*;
import com.example.art_course.modules.order.service.OrderService;
import com.example.art_course.modules.payment.service.EnrollmentGrantService;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import jakarta.persistence.EntityManager;
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
    private final CourseRepository courses = mock(CourseRepository.class);
    private final SupplyRepository supplies = mock(SupplyRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final ConfigService config = mock(ConfigService.class);
    private final TeacherNames names = mock(TeacherNames.class);
    private final OrderService service = new OrderService(orders, details, mock(CartRepository.class),
            mock(CartItemRepository.class), courses, supplies, mock(EntityManager.class), names, children,
            mock(EnrollmentGrantService.class), config);
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
