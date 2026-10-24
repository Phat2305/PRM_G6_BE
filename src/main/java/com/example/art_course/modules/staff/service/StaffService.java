package com.example.art_course.modules.staff.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.contracts.IModerationContract;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.repository.SupplyRepository;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffService {

    private final SupplyRepository supplyRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final RefundRepository refundRepository;
    private final IModerationContract moderationContract;

    /**
     * Staff kiểm duyệt / ẩn / gỡ dụng cụ vi phạm (STF-01, STF-07).
     */
    @Transactional
    public Supply moderateSupply(UUID supplyId, UUID staffId, String action, String reason) {
        Supply supply = supplyRepository.findById(supplyId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "SUPPLY_NOT_FOUND", "Không tìm thấy dụng cụ: " + supplyId));

        String cleanAction = action != null ? action.trim().toUpperCase() : "";
        String decision;

        if ("APPROVE".equals(cleanAction)) {
            supply.setStatus("ACTIVE");
            supply.syncStatusWithStock(); // duyệt khi kho đang 0 thì hiện hết hàng
            decision = "APPROVED";
        } else if ("REJECT".equals(cleanAction)) {
            supply.setStatus("REJECTED");
            decision = "REJECTED";
        } else if ("HIDE".equals(cleanAction)) {
            supply.setStatus("HIDDEN");
            decision = "FLAGGED";
        } else {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_ACTION", "Hành động kiểm duyệt không hợp lệ (APPROVE, REJECT, HIDE)");
        }

        Supply saved = supplyRepository.save(supply);

        // Lưu vào moderation_reviews
        moderationContract.recordModerationReview(staffId, "SUPPLY", supply.getId(), decision, reason);

        return saved;
    }

    /**
     * Danh sách đơn hàng có khiếu nại hoặc tranh chấp (STF-02, MSG-06).
     */
    @Transactional(readOnly = true)
    public List<Order> getComplaintOrders() {
        // Tìm các đơn có yêu cầu hoàn tiền đang chờ hoặc dòng giao thất bại
        List<Refund> pendingRefunds = refundRepository.findByStatus("PENDING");
        Set<UUID> orderIdsWithComplaints = new HashSet<>();
        for (Refund r : pendingRefunds) {
            orderIdsWithComplaints.add(r.getOrderId());
        }

        return orderRepository.findAllById(orderIdsWithComplaints);
    }

    /**
     * MSG-06: Staff chỉ được đọc nội dung trao đổi khi xử lý khiếu nại liên quan.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getComplaintConversation(UUID orderId, UUID staffId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng: " + orderId));

        // Kiểm tra xem đơn có khiếu nại / hoàn tiền hoặc giao hàng thất bại không
        List<Refund> refunds = refundRepository.findByOrderId(orderId);
        boolean hasComplaint = refunds.stream().anyMatch(r -> !"REJECTED".equalsIgnoreCase(r.getStatus()));

        if (!hasComplaint) {
            List<OrderDetail> details = orderDetailRepository.findByOrderId(orderId);
            hasComplaint = details.stream().anyMatch(d -> d.getDeliveryStatus() == DeliveryStatus.DELIVERY_FAILED);
        }

        if (!hasComplaint) {
            throw new AppError(HttpStatus.FORBIDDEN, "NO_COMPLAINT_RELATED",
                    "BẢO MẬT (MSG-06): Staff chỉ được quyền xem cuộc trao đổi khi đơn hàng có khiếu nại hoặc sự cố đang chờ xử lý.");
        }

        return Map.of(
                "orderId", orderId,
                "parentId", order.getParentId(),
                "teacherId", order.getTeacherId(),
                "authorizedStaffId", staffId,
                "accessReason", "Xử lý khiếu nại cho đơn hàng " + orderId
        );
    }
}
