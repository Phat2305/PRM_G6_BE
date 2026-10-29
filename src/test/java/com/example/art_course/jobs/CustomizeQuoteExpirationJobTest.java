package com.example.art_course.jobs;

import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.offer.entity.Offer;
import com.example.art_course.offer.repository.OfferRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Đơn customize hết hạn: đơn EXPIRED, yêu cầu về OPEN, báo giá về pending, thông báo cả hai bên")
class CustomizeQuoteExpirationJobTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final CustomizeQuoteExpirationJob job = new CustomizeQuoteExpirationJob(
            orders, details, requests, offers, notifications, mock(JobExecutionTracker.class));

    private final UUID requestId = UUID.randomUUID();

    private Order order(Instant expiredAt) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setParentId(UUID.randomUUID());
        o.setTeacherId(UUID.randomUUID());
        o.setStatus(OrderStatus.PENDING_PAYMENT);
        o.setExpiredAt(expiredAt);
        o.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(details.findByOrderIdAndCustomizeRequestIdIsNotNull(o.getId()))
                .thenReturn(List.of(OrderDetail.builder().customizeRequestId(requestId).build()));
        return o;
    }

    private CustomizeRequest request(String status) {
        CustomizeRequest r = new CustomizeRequest();
        r.setId(requestId);
        r.setStatus(status);
        when(requests.findById(requestId)).thenReturn(java.util.Optional.of(r));
        return r;
    }

    @Test
    @DisplayName("quá hạn theo expiredAt: mở lại yêu cầu và trả báo giá về pending")
    void expiredOrderReopensRequest() {
        Order expired = order(Instant.now().minusSeconds(60));
        when(orders.findIdsByStatus(OrderStatus.PENDING_PAYMENT)).thenReturn(List.of(expired.getId()));
        when(orders.findByIdForUpdate(expired.getId())).thenReturn(java.util.Optional.of(expired));
        CustomizeRequest request = request("ACCEPTED");
        Offer accepted = new Offer();
        accepted.setStatus("accepted");
        Offer rejected = new Offer();
        rejected.setStatus("rejected");
        Offer superseded = new Offer();
        superseded.setStatus("superseded");
        when(offers.findAllByRequestId(requestId)).thenReturn(List.of(accepted, rejected, superseded));

        assertEquals(1, job.expireOverdueCustomizeQuotes());

        assertEquals(OrderStatus.EXPIRED, expired.getStatus());
        assertEquals("OPEN", request.getStatus());
        assertEquals("pending", accepted.getStatus());
        assertEquals("pending", rejected.getStatus());
        assertEquals("superseded", superseded.getStatus());
        verify(notifications).notifyCustomizeQuoteExpired(requestId, expired.getParentId());
        verify(notifications).notifyCustomizeQuoteExpired(requestId, expired.getTeacherId());
    }

    @Test
    @DisplayName("chưa tới hạn thì giữ nguyên; yêu cầu không còn ACCEPTED thì không mở lại")
    void notYetDueOrNotAccepted() {
        Order fresh = order(Instant.now().plusSeconds(3600));
        Order late = order(Instant.now().minusSeconds(5));
        when(orders.findIdsByStatus(OrderStatus.PENDING_PAYMENT)).thenReturn(List.of(fresh.getId(), late.getId()));
        when(orders.findByIdForUpdate(fresh.getId())).thenReturn(java.util.Optional.of(fresh));
        when(orders.findByIdForUpdate(late.getId())).thenReturn(java.util.Optional.of(late));
        CustomizeRequest funded = request("FUNDED");

        assertEquals(1, job.expireOverdueCustomizeQuotes());

        assertEquals(OrderStatus.PENDING_PAYMENT, fresh.getStatus());
        assertEquals("FUNDED", funded.getStatus());
        verifyNoInteractions(notifications);
    }
}
