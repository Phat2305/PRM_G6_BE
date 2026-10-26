package com.example.art_course.modules.staff.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.contracts.IModerationContract;
import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.entity.Message;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.messaging.repository.MessageRepository;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.repository.SupplyRepository;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.staff.dto.ComplaintDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    private final TeacherNames names;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

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
     * Việc khiếu nại/sự cố Staff cần xử lý, mới nhất trước (STF-02, MSG-06): yêu cầu hoàn tiền đang chờ duyệt
     * và dòng dụng cụ giao thất bại, kèm tên phụ huynh và giáo viên.
     */
    @Transactional(readOnly = true)
    public List<ComplaintDto> getComplaints() {
        List<Refund> refunds = refundRepository.findByStatus("PENDING");
        List<OrderDetail> failed = orderDetailRepository.findByDeliveryStatus(DeliveryStatus.DELIVERY_FAILED);

        Set<UUID> orderIds = new HashSet<>();
        refunds.forEach(r -> orderIds.add(r.getOrderId()));
        failed.forEach(d -> orderIds.add(d.getOrderId()));
        Map<UUID, Order> orders = new HashMap<>();
        orderRepository.findAllById(orderIds).forEach(o -> orders.put(o.getId(), o));

        Map<UUID, String> parents = names.parentsByIds(orders.values().stream().map(Order::getParentId).distinct().toList());
        Map<UUID, String> teachers = names.byIds(orders.values().stream().map(Order::getTeacherId).distinct().toList());

        List<ComplaintDto> out = new ArrayList<>();
        for (Refund r : refunds) {
            Order o = orders.get(r.getOrderId());
            if (o == null) continue;
            out.add(new ComplaintDto("REFUND_REQUEST", o.getId(), null, r.getId(), o.getParentId(),
                    parents.get(o.getParentId()), o.getTeacherId(), teachers.get(o.getTeacherId()),
                    null, r.getReason(), r.getTotalAmount(), r.getCreatedAt()));
        }
        for (OrderDetail d : failed) {
            Order o = orders.get(d.getOrderId());
            if (o == null) continue;
            out.add(new ComplaintDto("DELIVERY_FAILED", o.getId(), d.getId(), null, o.getParentId(),
                    parents.get(o.getParentId()), o.getTeacherId(), teachers.get(o.getTeacherId()),
                    d.getProductName(), "Giao hàng thất bại", d.getSubTotal(), d.getUpdatedAt()));
        }
        out.sort(Comparator.comparing(ComplaintDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /**
     * MSG-06: Staff chỉ được đọc nội dung trao đổi khi xử lý khiếu nại liên quan.
     * Chat chỉ gắn với yêu cầu customize, nên chỉ trả tin của các cuộc trò chuyện thuộc yêu cầu có trong đơn này
     * giữa đúng phụ huynh và giáo viên của đơn; đơn dụng cụ thường thì không có tin nhắn.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getComplaintConversation(UUID orderId, UUID staffId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng: " + orderId));

        // Kiểm tra xem đơn có khiếu nại / hoàn tiền hoặc giao hàng thất bại không
        List<Refund> refunds = refundRepository.findByOrderId(orderId);
        List<OrderDetail> details = orderDetailRepository.findByOrderId(orderId);
        boolean hasComplaint = refunds.stream().anyMatch(r -> !"REJECTED".equalsIgnoreCase(r.getStatus()))
                || details.stream().anyMatch(d -> d.getDeliveryStatus() == DeliveryStatus.DELIVERY_FAILED);

        if (!hasComplaint) {
            throw new AppError(HttpStatus.FORBIDDEN, "NO_COMPLAINT_RELATED",
                    "BẢO MẬT (MSG-06): Staff chỉ được quyền xem cuộc trao đổi khi đơn hàng có khiếu nại hoặc sự cố đang chờ xử lý.");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (OrderDetail d : details) {
            if (d.getCustomizeRequestId() == null) continue;
            for (Conversation c : conversationRepository.findAllByContextTypeAndContextId(
                    "CUSTOMIZE_REQUEST", d.getCustomizeRequestId())) {
                if (!order.getTeacherId().equals(c.getTeacherId()) || !order.getParentId().equals(c.getParentId())) continue;
                for (Message m : messageRepository.findAllByConversationIdOrderByCreatedAtAsc(c.getId())) {
                    String role = m.getSenderId().equals(order.getParentId()) ? "PARENT" : "TEACHER";
                    messages.add(Map.of("senderRole", role, "content", m.getContent(), "createdAt", m.getCreatedAt()));
                }
            }
        }
        messages.sort(Comparator.comparing(m -> (OffsetDateTime) m.get("createdAt")));

        return Map.of(
                "orderId", orderId,
                "parentId", order.getParentId(),
                "teacherId", order.getTeacherId(),
                "authorizedStaffId", staffId,
                "accessReason", "Xử lý khiếu nại cho đơn hàng " + orderId,
                "messages", messages
        );
    }
}
