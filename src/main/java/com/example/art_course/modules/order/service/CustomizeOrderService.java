package com.example.art_course.modules.order.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.Money;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.entity.ChildrenCourse;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.ChildrenCourseRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.offer.entity.Offer;
import com.example.art_course.offer.repository.OfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// CUS-08, #27: đơn thanh toán cho yêu cầu customize đã chấp nhận báo giá (một dòng CUSTOMIZE, giá theo báo giá).
@Service
@RequiredArgsConstructor
public class CustomizeOrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ChildrenCourseRepository childrenCourseRepository;
    private final com.example.art_course.child.repository.ChildRepository childRepository;
    private final CustomizeRequestRepository requestRepository;
    private final OfferRepository offerRepository;
    private final ConfigService configService;

    /**
     * Trả về đơn đang chờ thanh toán của yêu cầu; chưa có (hoặc đơn cũ đã hết hạn/hủy) thì tạo mới.
     * Gọi lại nhiều lần không tạo đơn trùng.
     */
    @Transactional
    public Order orderFor(UUID requestId, UUID parentId) {
        return orderFor(requestId, parentId, null);
    }

    @Transactional
    public Order orderFor(UUID requestId, UUID parentId, UUID childId) {
        CustomizeRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu"));
        if (!parentId.equals(request.getParentId())) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Yêu cầu này không thuộc về bạn");
        }
        if (!"ACCEPTED".equalsIgnoreCase(request.getStatus())) {
            throw new AppError(HttpStatus.CONFLICT, "REQUEST_NOT_ACCEPTED",
                    "Yêu cầu chưa chấp nhận báo giá hoặc đã thanh toán");
        }

        for (OrderDetail detail : orderDetailRepository.findByCustomizeRequestId(requestId)) {
            Order existing = orderRepository.findByIdForUpdate(detail.getOrderId()).orElse(null);
            if (existing == null) continue;
            if (existing.getStatus() == OrderStatus.PENDING_PAYMENT
                    && (existing.getExpiredAt() == null || existing.getExpiredAt().isAfter(Instant.now()))) {
                addPendingChild(existing.getId(), detail, parentId, childId);
                return existing;
            }
            if (existing.getStatus() != OrderStatus.CANCELLED && existing.getStatus() != OrderStatus.EXPIRED
                    && existing.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new AppError(HttpStatus.CONFLICT, "ALREADY_PAID", "Yêu cầu này đã được thanh toán");
            }
        }

        Offer offer = offerRepository.findAllByRequestId(requestId).stream()
                .filter(o -> "accepted".equalsIgnoreCase(o.getStatus()))
                .findFirst()
                .orElseThrow(() -> new AppError(HttpStatus.CONFLICT, "NO_ACCEPTED_OFFER",
                        "Không tìm thấy báo giá đã chấp nhận"));

        Money price = Money.of(offer.getPrice());
        // Customize là khóa học nên dùng tỷ lệ hoa hồng khóa học; PaymentService tính lại lúc tạo thanh toán.
        var commission = price.calculateCommission(configService.getCommissionRateCourse());

        Order order = orderRepository.save(Order.builder()
                .parentId(parentId)
                .teacherId(offer.getTeacherId())
                .totalAmount(price.getAmount())
                .status(OrderStatus.PENDING_PAYMENT)
                .expiredAt(Instant.now().plus(configService.getQuotePaymentTimeoutHours(), ChronoUnit.HOURS))
                .build());

        OrderDetail detail = OrderDetail.builder()
                .orderId(order.getId())
                .customizeRequestId(requestId)
                .productName(request.getTitle())
                .unitPrice(price.getAmount())
                .quantity(1)
                .subTotal(price.getAmount())
                .commissionRate(configService.getCommissionRateCourse())
                .commissionAmount(commission.commission().getAmount())
                .netAmount(commission.net().getAmount())
                .build();
        orderDetailRepository.save(detail);
        addPendingChild(order.getId(), detail, parentId, childId);
        return order;
    }

    private void addPendingChild(UUID orderId, OrderDetail detail, UUID parentId, UUID childId) {
        if (childId == null) return;
        childRepository.findByUserIdAndParentId(childId, parentId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "CHILD_NOT_FOUND", "Không tìm thấy bé của bạn"));
        var assigned = childrenCourseRepository.findByOrderId(orderId);
        if (assigned.stream().anyMatch(a -> childId.equals(a.getChildId()))) return;
        childrenCourseRepository.save(ChildrenCourse.builder()
                .orderId(orderId).childId(childId)
                .price(assigned.isEmpty() ? detail.getUnitPrice() : java.math.BigDecimal.ZERO).build());
        if (detail.getChildId() == null) {
            detail.setChildId(childId);
            orderDetailRepository.save(detail);
        }
    }

    /** Hủy các đơn còn chờ thanh toán của yêu cầu (khi phụ huynh thu hồi báo giá đã chấp nhận). */
    @Transactional
    public void cancelPending(UUID requestId) {
        for (OrderDetail detail : orderDetailRepository.findByCustomizeRequestId(requestId)) {
            Order order = orderRepository.findByIdForUpdate(detail.getOrderId()).orElse(null);
            if (order == null) continue;
            if (order.getStatus() == OrderStatus.PAID) {
                throw new AppError(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "Yêu cầu đã được thanh toán");
            }
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
            }
        }
    }
}
