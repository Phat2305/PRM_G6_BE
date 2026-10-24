package com.example.art_course.modules.fulfillment.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.StateMachineValidator;
import com.example.art_course.modules.fulfillment.dto.DeliveryStatusResponseDto;
import com.example.art_course.modules.fulfillment.dto.ShippingUpdateRequest;
import com.example.art_course.modules.fulfillment.dto.TeacherConfirmDeliveryRequest;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.repository.SupplyRepository;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.entity.RefundItem;
import com.example.art_course.modules.refund.repository.RefundItemRepository;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private final OrderDetailRepository orderDetailRepository;
    private final OrderRepository orderRepository;
    private final SupplyRepository supplyRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final NotificationService notificationService;
    private final ConfigService configService;

    /**
     * Teacher xác nhận hoặc từ chối dòng dụng cụ (FUL-01).
     */
    @Transactional
    public DeliveryStatusResponseDto teacherConfirmOrReject(UUID orderDetailId, UUID teacherId, TeacherConfirmDeliveryRequest request) {
        OrderDetail detail = findSupplyDetail(orderDetailId);
        Order order = findOrder(detail.getOrderId());

        if (!order.getTeacherId().equals(teacherId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền quản lý dòng hàng này");
        }

        String action = request.getAction().toUpperCase();
        if ("CONFIRM".equals(action)) {
            validateTransition(detail.getDeliveryStatus(), DeliveryStatus.CONFIRMED);
            detail.setDeliveryStatus(DeliveryStatus.CONFIRMED);
            orderDetailRepository.save(detail);
            notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "CONFIRMED");
        } else if ("REJECT".equals(action)) {
            validateTransition(detail.getDeliveryStatus(), DeliveryStatus.REJECTED);
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new AppError(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Vui lòng nhập lý do từ chối");
            }
            detail.setDeliveryStatus(DeliveryStatus.REJECTED);
            orderDetailRepository.save(detail);

            // Hoàn tồn kho
            restoreSupplyInventory(detail);

            // Tạo yêu cầu hoàn tiền tự động cho dòng bị từ chối
            createRefundRequest(order, detail, "Giáo viên từ chối giao hàng: " + request.getReason());

            notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "REJECTED");
        } else {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_ACTION", "Hành động chỉ có thể là CONFIRM hoặc REJECT");
        }

        return mapToDto(detail);
    }

    /**
     * Teacher nhập đơn vị vận chuyển, mã vận đơn và cập nhật SHIPPING (FUL-02).
     */
    @Transactional
    public DeliveryStatusResponseDto teacherUpdateShipping(UUID orderDetailId, UUID teacherId, ShippingUpdateRequest request) {
        OrderDetail detail = findSupplyDetail(orderDetailId);
        Order order = findOrder(detail.getOrderId());

        if (!order.getTeacherId().equals(teacherId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền cập nhật vận chuyển cho dòng hàng này");
        }

        validateTransition(detail.getDeliveryStatus(), DeliveryStatus.SHIPPING);

        detail.setShippingProvider(request.getShippingProvider());
        detail.setTrackingCode(request.getTrackingCode());
        detail.setShippedAt(Instant.now());
        detail.setDeliveryStatus(DeliveryStatus.SHIPPING);
        orderDetailRepository.save(detail);

        notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "SHIPPING");
        return mapToDto(detail);
    }

    /**
     * Báo hàng đã giao tới nơi (FUL-03: DELIVERED - chỉ Teacher chủ đơn gọi được).
     */
    @Transactional
    public DeliveryStatusResponseDto markDelivered(UUID orderDetailId, UUID teacherId) {
        OrderDetail detail = findSupplyDetail(orderDetailId);
        Order order = findOrder(detail.getOrderId());

        if (!order.getTeacherId().equals(teacherId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ Giáo viên phụ trách đơn hàng mới có quyền báo đã giao hàng (FUL-03)");
        }

        validateTransition(detail.getDeliveryStatus(), DeliveryStatus.DELIVERED);

        detail.setDeliveredAt(Instant.now());
        detail.setDeliveryStatus(DeliveryStatus.DELIVERED);
        orderDetailRepository.save(detail);

        notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "DELIVERED");
        return mapToDto(detail);
    }

    /**
     * Parent xác nhận đã nhận hàng (FUL-03: DELIVERED -> COMPLETED).
     */
    @Transactional
    public DeliveryStatusResponseDto parentConfirmReceived(UUID orderDetailId, UUID parentId) {
        OrderDetail detail = findSupplyDetail(orderDetailId);
        Order order = findOrder(detail.getOrderId());

        if (!order.getParentId().equals(parentId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền xác nhận đơn hàng này");
        }

        if (detail.getDeliveryStatus() == DeliveryStatus.SHIPPING) {
            // Cho phép phụ huynh nhận hàng sớm khi đang giao
            detail.setDeliveredAt(Instant.now());
            detail.setDeliveryStatus(DeliveryStatus.DELIVERED);
        }

        validateTransition(detail.getDeliveryStatus(), DeliveryStatus.COMPLETED);

        detail.setConfirmedDeliveredAt(Instant.now());
        detail.setDeliveryStatus(DeliveryStatus.COMPLETED);
        orderDetailRepository.save(detail);

        notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "COMPLETED");
        return mapToDto(detail);
    }

    /**
     * Parent hủy dòng khi Teacher chưa xác nhận (FUL-06: PENDING -> CANCELLED).
     */
    @Transactional
    public DeliveryStatusResponseDto parentCancelItem(UUID orderDetailId, UUID parentId, String reason) {
        OrderDetail detail = findSupplyDetail(orderDetailId);
        Order order = findOrder(detail.getOrderId());

        if (!order.getParentId().equals(parentId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền hủy dòng hàng này");
        }

        if (detail.getDeliveryStatus() != DeliveryStatus.PENDING) {
            throw new AppError(HttpStatus.BAD_REQUEST, "CANNOT_CANCEL", "Chỉ có thể hủy khi dòng dụng cụ đang ở trạng thái chờ xác nhận (PENDING)");
        }

        validateTransition(detail.getDeliveryStatus(), DeliveryStatus.CANCELLED);

        detail.setDeliveryStatus(DeliveryStatus.CANCELLED);
        orderDetailRepository.save(detail);

        // Hoàn tồn kho
        restoreSupplyInventory(detail);

        // Tạo yêu cầu hoàn tiền tự động cho dòng bị hủy
        createRefundRequest(order, detail, "Phụ huynh hủy dòng hàng: " + (reason != null ? reason : "Không nêu lý do"));

        notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "CANCELLED");
        return mapToDto(detail);
    }

    /**
     * Báo giao hàng thất bại (FUL-07: SHIPPING -> DELIVERY_FAILED) chuyển cho Staff/Teacher xử lý.
     */
    @Transactional
    public DeliveryStatusResponseDto markDeliveryFailed(UUID orderDetailId, String reason) {
        OrderDetail detail = findSupplyDetail(orderDetailId);
        Order order = findOrder(detail.getOrderId());

        validateTransition(detail.getDeliveryStatus(), DeliveryStatus.DELIVERY_FAILED);

        detail.setDeliveryStatus(DeliveryStatus.DELIVERY_FAILED);
        orderDetailRepository.save(detail);

        notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "DELIVERY_FAILED");
        return mapToDto(detail);
    }

    /**
     * Staff xử lý giao thất bại / trả hàng (FUL-08): Giao lại hoặc Hoàn tiền.
     */
    @Transactional
    public DeliveryStatusResponseDto staffHandleDeliveryFailed(UUID orderDetailId, UUID staffId, String action, String reason) {
        OrderDetail detail = findSupplyDetail(orderDetailId);
        Order order = findOrder(detail.getOrderId());

        if (detail.getDeliveryStatus() != DeliveryStatus.DELIVERY_FAILED) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Dòng hàng không ở trạng thái giao thất bại (DELIVERY_FAILED)");
        }

        if ("RE_SHIP".equalsIgnoreCase(action)) {
            validateTransition(detail.getDeliveryStatus(), DeliveryStatus.SHIPPING);
            detail.setDeliveryStatus(DeliveryStatus.SHIPPING);
            orderDetailRepository.save(detail);
            notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "SHIPPING");
        } else if ("REFUND".equalsIgnoreCase(action)) {
            validateTransition(detail.getDeliveryStatus(), DeliveryStatus.REFUNDED);
            detail.setDeliveryStatus(DeliveryStatus.REFUNDED);
            orderDetailRepository.save(detail);

            restoreSupplyInventory(detail);
            createRefundRequest(order, detail, "Staff duyệt hoàn tiền giao hàng thất bại: " + reason);

            notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "REFUNDED");
        } else {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_ACTION", "Hành động Staff chỉ có thể là RE_SHIP hoặc REFUND");
        }

        return mapToDto(detail);
    }

    /**
     * Tự động xác nhận nhận hàng sau M ngày từ khi giao (FUL-05).
     * Phải idempotent (chạy hai lần không tác dụng kép).
     */
    @Transactional
    public int autoConfirmDeliveredItems() {
        int autoConfirmDays = configService.getAutoConfirmDeliveryDays();
        Instant threshold = Instant.now().minus(autoConfirmDays, ChronoUnit.DAYS);

        List<OrderDetail> deliveredItems = orderDetailRepository.findByDeliveryStatusAndDeliveredAtBefore(
                DeliveryStatus.DELIVERED, threshold
        );

        int count = 0;
        for (OrderDetail detail : deliveredItems) {
            if (detail.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
                detail.setDeliveryStatus(DeliveryStatus.COMPLETED);
                detail.setConfirmedDeliveredAt(Instant.now());
                orderDetailRepository.save(detail);

                Order order = orderRepository.findById(detail.getOrderId()).orElse(null);
                if (order != null) {
                    notificationService.notifyDeliveryStatusChanged(detail.getId(), order.getParentId(), order.getTeacherId(), "COMPLETED");
                }
                count++;
            }
        }

        log.info("[JOB-AUTO-CONFIRM] Đã tự động xác nhận hoàn thành {} dòng dụng cụ quá {} ngày", count, autoConfirmDays);
        return count;
    }

    private OrderDetail findSupplyDetail(UUID orderDetailId) {
        OrderDetail detail = orderDetailRepository.findById(orderDetailId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_DETAIL_NOT_FOUND", "Không tìm thấy dòng chi tiết đơn hàng: " + orderDetailId));
        if (!"SUPPLY".equalsIgnoreCase(detail.getProductType())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "NOT_SUPPLY_ITEM", "Dòng hàng này không phải dụng cụ vẽ");
        }
        return detail;
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
    }

    private void validateTransition(DeliveryStatus current, DeliveryStatus next) {
        if (!StateMachineValidator.isValidDeliveryTransition(current, next)) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_STATE_TRANSITION",
                    String.format("Không thể chuyển trạng thái giao hàng từ %s sang %s", current, next));
        }
    }

    private void restoreSupplyInventory(OrderDetail detail) {
        if (detail.getSupplyId() != null) {
            Supply supply = supplyRepository.findByIdForUpdate(detail.getSupplyId()).orElse(null);
            if (supply != null) {
                supply.setStockQuantity(supply.getStockQuantity() + detail.getQuantity());
                supply.syncStatusWithStock();
                supply.setSoldQuantity(Math.max(0, supply.getSoldQuantity() - detail.getQuantity()));
                supplyRepository.save(supply);
            }
        }
    }

    private void createRefundRequest(Order order, OrderDetail detail, String reason) {
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        UUID paymentId = payment != null ? payment.getId() : UUID.randomUUID();

        Refund refund = Refund.builder()
                .paymentId(paymentId)
                .orderId(order.getId())
                .parentId(order.getParentId())
                .totalAmount(detail.getSubTotal())
                .bankInfo("AUTO_REFUND_GATEWAY")
                .reason(reason)
                .status("PENDING")
                .build();
        Refund savedRefund = refundRepository.save(refund);

        RefundItem refundItem = RefundItem.builder()
                .refundId(savedRefund.getId())
                .orderDetailId(detail.getId())
                .amount(detail.getSubTotal())
                .reason(reason)
                .build();
        refundItemRepository.save(refundItem);
    }

    private DeliveryStatusResponseDto mapToDto(OrderDetail detail) {
        return DeliveryStatusResponseDto.builder()
                .orderDetailId(detail.getId())
                .orderId(detail.getOrderId())
                .productName(detail.getProductName())
                .deliveryStatus(detail.getDeliveryStatus())
                .shippingProvider(detail.getShippingProvider())
                .trackingCode(detail.getTrackingCode())
                .shippedAt(detail.getShippedAt())
                .deliveredAt(detail.getDeliveredAt())
                .confirmedDeliveredAt(detail.getConfirmedDeliveredAt())
                .build();
    }
}
