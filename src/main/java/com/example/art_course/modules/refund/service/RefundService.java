package com.example.art_course.modules.refund.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.moderation.service.ModerationService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.repository.SupplyRepository;
import com.example.art_course.modules.refund.dto.CompleteRefundRequest;
import com.example.art_course.modules.refund.dto.CreateRefundRequest;
import com.example.art_course.modules.refund.dto.RefundResponseDto;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.entity.RefundItem;
import com.example.art_course.modules.refund.repository.RefundItemRepository;
import com.example.art_course.modules.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service("be2RefundService")
@RequiredArgsConstructor
public class RefundService {

    private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "COMPLETED", "FAILED");

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final PaymentRepository paymentRepository;
    private final SupplyRepository supplyRepository;
    private final IEnrollmentContract enrollmentContract;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final ModerationService moderationService;
    private final CustomizeRequestRepository customizeRequestRepository;
    private final CourseRepository courseRepository;

    /**
     * Parent gửi yêu cầu hoàn tiền cho 1 dòng chi tiết trong đơn hàng (FIN-07).
     * Kiểm tra: tổng số tiền hoàn không được vượt quá số tiền đã thanh toán của đơn hàng.
     */
    @Transactional
    public RefundResponseDto requestRefund(UUID parentId, CreateRefundRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng: " + request.getOrderId()));

        if (!order.getParentId().equals(parentId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền yêu cầu hoàn tiền cho đơn hàng này");
        }

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new AppError(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_FOUND", "Đơn hàng chưa có giao dịch thanh toán"));

        OrderDetail detail = orderDetailRepository.findById(request.getOrderDetailId())
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_DETAIL_NOT_FOUND", "Không tìm thấy dòng sản phẩm"));

        if (!detail.getOrderId().equals(order.getId())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_ORDER_DETAIL", "Dòng sản phẩm không thuộc đơn hàng này");
        }

        // Kiểm tra tổng hoàn không vượt số tiền đã thanh toán (FIN-07, PAY-08)
        List<Refund> existingRefunds = refundRepository.findByOrderId(order.getId());
        BigDecimal totalRefundedOrPending = existingRefunds.stream()
                .filter(r -> !"REJECTED".equalsIgnoreCase(r.getStatus()))
                .map(Refund::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotalRefund = totalRefundedOrPending.add(detail.getSubTotal());
        if (newTotalRefund.compareTo(payment.getAmount()) > 0) {
            throw new AppError(HttpStatus.BAD_REQUEST, "REFUND_EXCEEDS_PAYMENT",
                    String.format("Tổng số tiền hoàn (%s VND) vượt quá số tiền đã thanh toán của đơn hàng (%s VND)",
                            newTotalRefund, payment.getAmount()));
        }

        Refund refund = Refund.builder()
                .paymentId(payment.getId())
                .orderId(order.getId())
                .parentId(parentId)
                .totalAmount(detail.getSubTotal())
                .bankInfo(request.getBankInfo())
                .reason(request.getReason())
                .status("PENDING")
                .build();
        Refund savedRefund = refundRepository.save(refund);

        RefundItem refundItem = RefundItem.builder()
                .refundId(savedRefund.getId())
                .orderDetailId(detail.getId())
                .amount(detail.getSubTotal())
                .reason(request.getReason())
                .build();
        refundItemRepository.save(refundItem);

        log.info("[REFUND-REQUEST] Parent {} gửi yêu cầu hoàn {} VND cho mục {} thuộc đơn {}",
                parentId, detail.getSubTotal(), detail.getId(), order.getId());

        return mapToDto(savedRefund);
    }

    /**
     * Staff/Admin duyệt yêu cầu hoàn tiền (FIN-08).
     * Thu hồi quyền học nếu là khóa học (BR-12), trả tồn kho nếu là dụng cụ chưa giao.
     */
    @Transactional
    public RefundResponseDto approveRefund(UUID refundId, UUID staffId) {
        Refund refund = findRefund(refundId);

        if (!"PENDING".equalsIgnoreCase(refund.getStatus())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Yêu cầu hoàn tiền không ở trạng thái chờ duyệt (PENDING)");
        }

        refund.setStatus("APPROVED");
        refund.setHandledBy(staffId);
        Refund updated = refundRepository.save(refund);

        List<RefundItem> items = refundItemRepository.findByRefundId(refund.getId());
        for (RefundItem item : items) {
            OrderDetail detail = orderDetailRepository.findById(item.getOrderDetailId()).orElse(null);
            if (detail != null) {
                if ("COURSE".equalsIgnoreCase(detail.getProductType())) {
                    // Thu hồi quyền học BE1 (BR-12)
                    enrollmentContract.revokeAccess(detail.getChildId(), detail.getCourseId(), refund.getId());
                    log.info("[REFUND-REVOKE] Đã thu hồi quyền học của Child {} - Course {}", detail.getChildId(), detail.getCourseId());
                } else if ("SUPPLY".equalsIgnoreCase(detail.getProductType())) {
                    // Trả lại tồn kho nếu dụng cụ chưa hoàn tất giao
                    if (detail.getDeliveryStatus() != DeliveryStatus.COMPLETED) {
                        restoreSupplyStock(detail);
                    }
                    detail.setDeliveryStatus(DeliveryStatus.REFUNDED);
                    orderDetailRepository.save(detail);
                } else if ("CUSTOMIZE".equalsIgnoreCase(detail.getProductType())) {
                    // Hoàn tiền đơn khóa riêng (phụ huynh không thống nhất với giáo viên hoặc khóa không qua kiểm duyệt):
                    // yêu cầu bị hủy, không bàn giao/nhận nữa; bé đã được gán thì thu hồi quyền học.
                    detail.setDeliveryStatus(DeliveryStatus.REFUNDED);
                    orderDetailRepository.save(detail);
                    customizeRequestRepository.findById(detail.getCustomizeRequestId()).ifPresent(request -> {
                        request.setStatus("CANCELLED");
                        customizeRequestRepository.save(request);
                    });
                    if (detail.getChildId() != null) {
                        courseRepository.findFirstByCustomizeRequestId(detail.getCustomizeRequestId())
                                .ifPresent(course -> enrollmentContract.revokeAccess(
                                        detail.getChildId(), course.getId(), refund.getId()));
                    }
                }
            }
        }

        log.info("[REFUND-APPROVE] Staff {} đã duyệt yêu cầu hoàn tiền {}", staffId, refundId);
        return mapToDto(updated);
    }

    /**
     * Staff/Admin từ chối yêu cầu hoàn tiền đang chờ duyệt, bắt buộc lý do (FIN-08).
     * Lý do ghi vào nhật ký kiểm duyệt (REFUND) và gửi cho Parent; chưa duyệt nên chưa có quyền học/tồn kho cần hoàn lại.
     */
    @Transactional
    public RefundResponseDto rejectRefund(UUID refundId, UUID staffId, String reason) {
        Refund refund = findRefund(refundId);

        if (!"PENDING".equalsIgnoreCase(refund.getStatus())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Chỉ từ chối được yêu cầu hoàn tiền đang chờ duyệt (PENDING)");
        }

        refund.setStatus("REJECTED");
        refund.setHandledBy(staffId);
        Refund updated = refundRepository.save(refund);

        moderationService.recordModerationReview(staffId, "REFUND", refundId, "REJECTED", reason);
        notificationService.notifyRefundRejected(refundId, refund.getParentId(), refund.getTotalAmount(), reason);
        log.info("[REFUND-REJECT] Staff {} đã từ chối yêu cầu hoàn tiền {}", staffId, refundId);
        return mapToDto(updated);
    }

    /**
     * Staff ghi nhận chuyển khoản hoàn tiền thành công kèm mã GD + ảnh ủy nhiệm chi (FIN-08, FIN-09).
     * Ghi sổ cái REFUND.
     */
    @Transactional
    public RefundResponseDto completeRefund(UUID refundId, UUID staffId, CompleteRefundRequest request) {
        Refund refund = findRefund(refundId);

        if (!"APPROVED".equalsIgnoreCase(refund.getStatus())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Yêu cầu hoàn tiền phải được duyệt (APPROVED) trước khi hoàn tất");
        }

        refund.setStatus("COMPLETED");
        refund.setRefundTransactionCode(request.getTransactionCode());
        refund.setProofImageUrl(request.getProofImageUrl());
        refund.setHandledBy(staffId);
        Refund updated = refundRepository.save(refund);

        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElse(null);
        UUID teacherId = payment != null ? payment.getTeacherId() : UUID.randomUUID();

        // Ghi sổ cái REFUND
        ledgerService.recordEntry(
                teacherId,
                "REFUND",
                refund.getTotalAmount(),
                refund.getPaymentId(),
                refund.getId(),
                null,
                "Hoàn tiền thành công cho đơn hàng " + refund.getOrderId() + " qua mã GD: " + request.getTransactionCode(),
                staffId
        );

        notificationService.notifyRefundCompleted(updated.getId(), updated.getParentId(), updated.getTotalAmount(), request.getTransactionCode());
        log.info("[REFUND-COMPLETED] Staff {} hoàn tất chi trả hoàn tiền {} VND cho Parent {}",
                staffId, updated.getTotalAmount(), updated.getParentId());

        return mapToDto(updated);
    }

    /**
     * Staff/Admin xem danh sách yêu cầu hoàn tiền, mới nhất trước, lọc theo trạng thái nếu có (FIN-08).
     */
    @Transactional(readOnly = true)
    public List<RefundResponseDto> listRefunds(String status, int page, int size) {
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (wanted != null && !STATUSES.contains(wanted)) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Trạng thái hoàn tiền không hợp lệ: " + status);
        }
        // id làm khóa phụ để các dòng cùng createdAt không bị lặp hoặc sót giữa các trang.
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Refund> result = wanted == null
                ? refundRepository.findAll(pageable)
                : refundRepository.findByStatus(wanted, pageable);
        return result.stream().map(this::mapToDto).toList();
    }

    /**
     * Parent xem các yêu cầu hoàn tiền của chính mình (FIN-07).
     */
    @Transactional(readOnly = true)
    public List<RefundResponseDto> listMyRefunds(UUID parentId) {
        return refundRepository.findByParentIdOrderByCreatedAtDesc(parentId).stream().map(r -> {
            RefundResponseDto dto = mapToDto(r);
            if ("REJECTED".equalsIgnoreCase(r.getStatus())) {
                // Mới nhất trước: lấy lý do của lần từ chối gần nhất.
                moderationService.getReviewsByTarget("REFUND", r.getId()).stream()
                        .filter(m -> "REJECTED".equalsIgnoreCase(m.getDecision()))
                        .findFirst()
                        .ifPresent(m -> dto.setReviewNote(m.getReason()));
            }
            return dto;
        }).toList();
    }

    private Refund findRefund(UUID refundId) {
        return refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "Không tìm thấy yêu cầu hoàn tiền: " + refundId));
    }

    private void restoreSupplyStock(OrderDetail detail) {
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

    private RefundResponseDto mapToDto(Refund r) {
        return RefundResponseDto.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .paymentId(r.getPaymentId())
                .parentId(r.getParentId())
                .totalAmount(r.getTotalAmount())
                .bankInfo(r.getBankInfo())
                .reason(r.getReason())
                .status(r.getStatus())
                .refundTransactionCode(r.getRefundTransactionCode())
                .proofImageUrl(r.getProofImageUrl())
                .handledBy(r.getHandledBy())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
