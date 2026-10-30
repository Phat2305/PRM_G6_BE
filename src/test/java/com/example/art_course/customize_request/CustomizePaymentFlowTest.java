package com.example.art_course.customize_request;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.entity.ChildrenCourse;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.order.service.CustomizeOrderService;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.offer.dto.UpdateOfferStatusRequest;
import com.example.art_course.offer.entity.Offer;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.offer.service.OfferService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CUS-05/08: chấp nhận báo giá -> yêu cầu ACCEPTED; tạo đơn CUSTOMIZE theo giá báo giá để thanh toán.
class CustomizePaymentFlowTest {

    private final OfferRepository offers = mock(OfferRepository.class);
    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final com.example.art_course.modules.order.repository.ChildrenCourseRepository childrenCourses =
            mock(com.example.art_course.modules.order.repository.ChildrenCourseRepository.class);
    private final com.example.art_course.child.repository.ChildRepository children =
            mock(com.example.art_course.child.repository.ChildRepository.class);
    private final ConfigService config = mock(ConfigService.class);

    private final UUID parentId = UUID.randomUUID();
    private final CustomizeRequest request = CustomizeRequest.builder()
            .id(UUID.randomUUID()).parentId(parentId).title("Vẽ mèo").status("OPEN").build();

    private Offer offer(String status) {
        return Offer.builder().id(UUID.randomUUID()).requestId(request.getId())
                .teacherId(UUID.randomUUID()).price(new BigDecimal("400000")).status(status).build();
    }

    private UpdateOfferStatusRequest status(String s) {
        UpdateOfferStatusRequest r = new UpdateOfferStatusRequest();
        r.setStatus(s);
        return r;
    }

    private void loginParent() {
        User u = new User(parentId, "p", "h", "PARENT", "ACTIVE", null, null);
        when(users.findByPrincipal(parentId.toString())).thenReturn(Optional.of(u));
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
    }

    @Test
    void acceptOffer_movesRequestToAccepted_rejectsOthers_andBlocksSecondAccept() {
        loginParent();
        Offer chosen = offer("pending");
        Offer other = offer("pending");
        when(offers.findById(chosen.getId())).thenReturn(Optional.of(chosen));
        when(offers.findById(other.getId())).thenReturn(Optional.of(other));
        when(offers.findAllByRequestId(request.getId())).thenReturn(List.of(chosen, other));
        when(offers.save(any())).thenAnswer(i -> i.getArgument(0));
        OfferService service = new OfferService(offers, requests, users,
                mock(com.example.art_course.messaging.repository.ConversationRepository.class),
                mock(com.example.art_course.messaging.repository.MessageRepository.class));

        service.updateStatus(chosen.getId(), status("accepted"), parentId.toString());

        assertEquals("accepted", chosen.getStatus());
        assertEquals("ACCEPTED", request.getStatus());
        assertEquals("rejected", other.getStatus());

        // Báo giá khác còn "pending" được giả lập lại: yêu cầu không còn OPEN nên không chấp nhận thêm
        other.setStatus("pending");
        assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(other.getId(), status("accepted"), parentId.toString()));
    }

    private CustomizeOrderService orderService() {
        when(config.getCommissionRateCourse()).thenReturn(new BigDecimal("0.20"));
        when(config.getQuotePaymentTimeoutHours()).thenReturn(24);
        when(orders.save(any())).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        return new CustomizeOrderService(orders, details, childrenCourses, children, requests, offers, config);
    }

    @Test
    void orderFor_createsCustomizeLineAtOfferPrice() {
        request.setStatus("ACCEPTED");
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        Offer accepted = offer("ACCEPTED");
        when(offers.findAllByRequestId(request.getId())).thenReturn(List.of(offer("rejected"), accepted));
        when(details.findByCustomizeRequestId(request.getId())).thenReturn(List.of());

        Order order = orderService().orderFor(request.getId(), parentId);

        assertEquals(accepted.getTeacherId(), order.getTeacherId());
        assertEquals(0, new BigDecimal("400000").compareTo(order.getTotalAmount()));
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertTrue(order.getExpiredAt().isAfter(Instant.now().plus(23, ChronoUnit.HOURS)));
        ArgumentCaptor<OrderDetail> line = ArgumentCaptor.forClass(OrderDetail.class);
        verify(details).save(line.capture());
        assertEquals("CUSTOMIZE", line.getValue().getProductType());
        assertEquals(0, new BigDecimal("80000").compareTo(line.getValue().getCommissionAmount()));
    }

    @Test
    void paymentCanAssignAChildInChildrenCourseWithoutRequiringOne() {
        request.setStatus("ACCEPTED");
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(details.findByCustomizeRequestId(request.getId())).thenReturn(List.of());
        when(offers.findAllByRequestId(request.getId())).thenReturn(List.of(offer("accepted")));
        UUID childId = UUID.randomUUID();
        when(children.findByUserIdAndParentId(childId, parentId)).thenReturn(Optional.of(new com.example.art_course.child.entity.Child()));

        CustomizeOrderService service = orderService();
        Order selected = service.orderFor(request.getId(), parentId, childId);
        ArgumentCaptor<ChildrenCourse> assigned = ArgumentCaptor.forClass(ChildrenCourse.class);
        verify(childrenCourses).save(assigned.capture());
        assertEquals(selected.getId(), assigned.getValue().getOrderId());
        assertNull(assigned.getValue().getCourseId());
        assertEquals(childId, assigned.getValue().getChildId());
        assertEquals(0, new BigDecimal("400000").compareTo(assigned.getValue().getPrice()));

        clearInvocations(childrenCourses);
        service.orderFor(request.getId(), parentId);
        verify(childrenCourses, never()).save(any());
    }

    @Test
    void orderFor_reusesPendingOrder_andRejectsWrongParentOrStatus() {
        request.setStatus("ACCEPTED");
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        Order pending = Order.builder().id(UUID.randomUUID()).parentId(parentId)
                .status(OrderStatus.PENDING_PAYMENT).expiredAt(Instant.now().plus(1, ChronoUnit.HOURS)).build();
        when(details.findByCustomizeRequestId(request.getId()))
                .thenReturn(List.of(OrderDetail.builder().orderId(pending.getId()).build()));
        when(orders.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        CustomizeOrderService service = orderService();

        assertSame(pending, service.orderFor(request.getId(), parentId));
        verify(orders, never()).save(any());

        AppError foreign = assertThrows(AppError.class, () -> service.orderFor(request.getId(), UUID.randomUUID()));
        assertEquals("FORBIDDEN", foreign.getErrorCode());

        request.setStatus("OPEN");
        AppError open = assertThrows(AppError.class, () -> service.orderFor(request.getId(), parentId));
        assertEquals("REQUEST_NOT_ACCEPTED", open.getErrorCode());
    }

    @Test
    void pendingCustomizeOrderCanSelectThirdChildWithoutExtraCharge() {
        request.setStatus("ACCEPTED");
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        Order pending = Order.builder().id(UUID.randomUUID()).parentId(parentId)
                .status(OrderStatus.PENDING_PAYMENT).expiredAt(Instant.now().plus(1, ChronoUnit.HOURS)).build();
        OrderDetail line = OrderDetail.builder().orderId(pending.getId())
                .unitPrice(new BigDecimal("400000")).build();
        when(details.findByCustomizeRequestId(request.getId())).thenReturn(List.of(line));
        when(orders.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        UUID childId = UUID.randomUUID();
        when(children.findByUserIdAndParentId(childId, parentId)).thenReturn(Optional.of(new com.example.art_course.child.entity.Child()));
        when(childrenCourses.findByOrderId(pending.getId())).thenReturn(List.of(
                ChildrenCourse.builder().childId(UUID.randomUUID()).build(),
                ChildrenCourse.builder().childId(UUID.randomUUID()).build()));

        assertSame(pending, orderService().orderFor(request.getId(), parentId, childId));

        ArgumentCaptor<ChildrenCourse> assigned = ArgumentCaptor.forClass(ChildrenCourse.class);
        verify(childrenCourses).save(assigned.capture());
        assertEquals(childId, assigned.getValue().getChildId());
        assertEquals(BigDecimal.ZERO, assigned.getValue().getPrice());
    }

    @Test
    void revokeCannotReopenARequestWhenPaymentWonTheOrderLock() {
        Order paid = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        when(details.findByCustomizeRequestId(request.getId()))
                .thenReturn(List.of(OrderDetail.builder().orderId(paid.getId()).build()));
        when(orders.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));
        assertEquals("ORDER_ALREADY_PAID", assertThrows(AppError.class,
                () -> orderService().cancelPending(request.getId())).getErrorCode());
        verify(orders, never()).save(any());
    }
}
