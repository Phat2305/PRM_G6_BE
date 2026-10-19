package com.example.art_course.modules.order.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.Money;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.modules.cart.entity.Cart;
import com.example.art_course.modules.cart.entity.CartItem;
import com.example.art_course.modules.cart.repository.CartItemRepository;
import com.example.art_course.modules.cart.repository.CartRepository;
import com.example.art_course.modules.course.entity.Course;
import com.example.art_course.modules.course.repository.CourseRepository;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.repository.SupplyRepository;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CourseRepository courseRepository;
    private final SupplyRepository supplyRepository;
    private final EntityManager entityManager;
    private final com.example.art_course.modules.systemconfig.service.ConfigService configService;

    /**
     * Tạo đơn hàng từ giỏ hoặc danh sách sản phẩm.
     * Tự động tách theo từng Teacher: mỗi Teacher đúng một đơn riêng (SHOP-05, BR-04, #25).
     * Khóa dòng PESSIMISTIC_WRITE để chống bán vượt tồn và ghi nhận giữ chỗ kho (PAY-07, BR-11).
     */
    @Transactional
    public List<Order> createOrdersFromCheckout(UUID parentId, CheckoutRequest request) {
        List<CartItem> itemsToProcess = new ArrayList<>();
        UUID cartId = null;

        if (request.isUseCart()) {
            Cart cart = cartRepository.findByParentId(parentId)
                    .orElseThrow(() -> new AppError(HttpStatus.BAD_REQUEST, "CART_EMPTY", "Giỏ hàng đang trống"));
            cartId = cart.getId();
            itemsToProcess = cartItemRepository.findByCartId(cartId);
            if (itemsToProcess.isEmpty()) {
                throw new AppError(HttpStatus.BAD_REQUEST, "CART_EMPTY", "Giỏ hàng đang trống");
            }
        } else {
            if (request.getItems() == null || request.getItems().isEmpty()) {
                throw new AppError(HttpStatus.BAD_REQUEST, "NO_ITEMS", "Không có sản phẩm nào được chọn để đặt hàng");
            }
            for (var it : request.getItems()) {
                itemsToProcess.add(CartItem.builder()
                        .productType(it.getItemType())
                        .courseId(it.getCourseId())
                        .supplyId(it.getSupplyId())
                        .childId(it.getChildId())
                        .quantity(it.getQuantity() > 0 ? it.getQuantity() : 1)
                        .build());
            }
        }

        // Nhóm theo Teacher
        Map<UUID, List<PreparedItem>> teacherGroups = new LinkedHashMap<>();

        BigDecimal commCourse = configService.getCommissionRateCourse();
        BigDecimal commSupply = configService.getCommissionRateSupply();

        for (CartItem item : itemsToProcess) {
            if ("COURSE".equalsIgnoreCase(item.getProductType())) {
                if (item.getCourseId() == null || item.getChildId() == null) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Khóa học phải có courseId và childId");
                }

                // Chặn mua trùng khóa học cho cùng 1 Child (SHOP-04)
                String enrollSql = "SELECT 1 FROM child_enroll WHERE child_id = :childId AND course_id = :courseId AND status = 'ACTIVE'";
                List<?> enrolled = entityManager.createNativeQuery(enrollSql)
                        .setParameter("childId", item.getChildId())
                        .setParameter("courseId", item.getCourseId())
                        .getResultList();

                if (!enrolled.isEmpty()) {
                    throw new AppError(HttpStatus.CONFLICT, "COURSE_ALREADY_OWNED", "Bé đã sở hữu khóa học này (SHOP-04)");
                }

                Course course = courseRepository.findById(item.getCourseId())
                        .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học"));

                if (!"PUBLISHED".equalsIgnoreCase(course.getStatus())) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "COURSE_UNAVAILABLE", "Khóa học chưa được công khai");
                }

                teacherGroups.computeIfAbsent(course.getTeacherId(), k -> new ArrayList<>())
                        .add(new PreparedItem("COURSE", course.getId(), null, item.getChildId(),
                                course.getName(), course.getPrice(), 1, commCourse));

            } else if ("SUPPLY".equalsIgnoreCase(item.getProductType())) {
                if (item.getSupplyId() == null) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Dụng cụ phải có supplyId");
                }

                // Khóa dòng PESSIMISTIC_WRITE để chống race condition (PAY-07, BR-11)
                Supply supply = supplyRepository.findByIdForUpdate(item.getSupplyId())
                        .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "SUPPLY_NOT_FOUND", "Không tìm thấy dụng cụ vẽ"));

                int qty = item.getQuantity();
                int available = supply.getAvailableStock();
                if (available < qty) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                            "Dụng cụ \"" + supply.getName() + "\" không đủ tồn kho (còn " + available + ", yêu cầu " + qty + ") (PAY-07)");
                }

                // Giữ chỗ tồn kho (PAY-07, BR-11)
                supply.setReservedQuantity(supply.getReservedQuantity() + qty);
                supplyRepository.save(supply);

                teacherGroups.computeIfAbsent(supply.getTeacherId(), k -> new ArrayList<>())
                        .add(new PreparedItem("SUPPLY", null, supply.getId(), null,
                                supply.getName(), supply.getPrice(), qty, commSupply));
            }
        }

        int timeoutMinutes = configService.getOrderPaymentTimeoutMinutes();
        Instant expiredAt = Instant.now().plus(timeoutMinutes, ChronoUnit.MINUTES);
        List<Order> createdOrders = new ArrayList<>();

        // Tạo mỗi Teacher một đơn hàng riêng biệt (BR-04, SHOP-05, #25)
        for (var entry : teacherGroups.entrySet()) {
            UUID teacherId = entry.getKey();
            List<PreparedItem> items = entry.getValue();

            Money subtotal = Money.ZERO;
            boolean hasSupply = false;

            for (PreparedItem it : items) {
                Money lineTotal = Money.of(it.unitPrice).multiply(it.quantity);
                subtotal = subtotal.add(lineTotal);
                if ("SUPPLY".equalsIgnoreCase(it.productType)) {
                    hasSupply = true;
                }
            }

            // Phí ship: đọc từ system_config (shipping_fee_default) hoặc cấu hình Teacher nếu có
            Money shippingFee = hasSupply ? resolveShippingFee(teacherId) : Money.ZERO;
            Money totalAmount = subtotal.add(shippingFee);

            Order order = Order.builder()
                    .parentId(parentId)
                    .teacherId(teacherId)
                    .totalAmount(totalAmount.getAmount())
                    .shippingFee(shippingFee.getAmount())
                    .status(OrderStatus.PENDING_PAYMENT)
                    .expiredAt(expiredAt)
                    .build();
            order = orderRepository.save(order);

            // Lưu snapshot vào bảng order_details (SHOP-03)
            for (PreparedItem it : items) {
                Money lineTotal = Money.of(it.unitPrice).multiply(it.quantity);
                var commResult = lineTotal.calculateCommission(it.commissionRate);

                OrderDetail detail = OrderDetail.builder()
                        .orderId(order.getId())
                        .productType(it.productType)
                        .courseId(it.courseId)
                        .supplyId(it.supplyId)
                        .childId(it.childId)
                        .productName(it.productName)
                        .unitPrice(it.unitPrice)
                        .quantity(it.quantity)
                        .subTotal(lineTotal.getAmount())
                        .commissionRate(it.commissionRate)
                        .commissionAmount(commResult.commission().getAmount())
                        .netAmount(commResult.net().getAmount())
                        .deliveryStatus("SUPPLY".equalsIgnoreCase(it.productType) ? DeliveryStatus.PENDING : null)
                        .build();
                orderDetailRepository.save(detail);
            }

            createdOrders.add(order);
        }

        // Xóa giỏ hàng nếu checkout từ giỏ
        if (cartId != null) {
            cartItemRepository.deleteByCartId(cartId);
        }

        return createdOrders;
    }

    /**
     * Xác định phí vận chuyển cho đơn hàng của Teacher:
     * Đọc từ bảng system_config (khóa shipping_fee_default) hoặc phí ship riêng của Teacher (nếu có trường trong DB).
     */
    public Money resolveShippingFee(UUID teacherId) {
        return configService.getDefaultShippingFee();
    }

    @Transactional(readOnly = true)
    public List<Order> getParentOrders(UUID parentId) {
        return orderRepository.findByParentIdOrderByCreatedAtDesc(parentId);
    }

    @Transactional(readOnly = true)
    public List<Order> getTeacherOrders(UUID teacherId) {
        return orderRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(UUID orderId, UUID userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));

        if ("PARENT".equalsIgnoreCase(role) && !order.getParentId().equals(userId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Đơn hàng này không thuộc về bạn");
        }
        if ("TEACHER".equalsIgnoreCase(role) && !order.getTeacherId().equals(userId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Đơn hàng này không thuộc về bạn");
        }

        List<OrderDetail> details = orderDetailRepository.findByOrderId(orderId);
        return new OrderDetailResponse(order, details);
    }

    /**
     * Hủy đơn hàng đang chờ thanh toán: giải phóng tồn kho giữ chỗ (BR-11).
     * Cho phép phụ huynh chủ sở hữu đơn hàng HOẶC STAFF/ADMIN hủy.
     */
    @Transactional
    public void cancelOrder(UUID orderId, UUID userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));

        boolean isOwner = "PARENT".equalsIgnoreCase(role) && order.getParentId().equals(userId);
        boolean isStaffOrAdmin = "STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);

        if (!isOwner && !isStaffOrAdmin) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền hủy đơn hàng này");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new AppError(HttpStatus.BAD_REQUEST, "CANNOT_CANCEL", "Chỉ có thể hủy đơn hàng đang chờ thanh toán");
        }

        // Hoàn trả tồn giữ chỗ
        List<OrderDetail> details = orderDetailRepository.findByOrderIdAndProductType(orderId, "SUPPLY");
        for (OrderDetail d : details) {
            if (d.getSupplyId() != null) {
                supplyRepository.findById(d.getSupplyId()).ifPresent(s -> {
                    s.setReservedQuantity(Math.max(0, s.getReservedQuantity() - d.getQuantity()));
                    supplyRepository.save(s);
                });
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    /**
     * Job quét đơn quá hạn: đơn PENDING_PAYMENT quá hạn -> EXPIRED và trả lại tồn kho (PAY-08).
     * An toàn khi chạy nhiều lần (Idempotency).
     */
    @Transactional
    public int expirePendingOrders() {
        List<Order> expiredOrders = orderRepository.findByStatusAndExpiredAtLessThanEqual(
                OrderStatus.PENDING_PAYMENT, Instant.now()
        );

        int count = 0;
        for (Order order : expiredOrders) {
            List<OrderDetail> details = orderDetailRepository.findByOrderIdAndProductType(order.getId(), "SUPPLY");
            for (OrderDetail d : details) {
                if (d.getSupplyId() != null) {
                    supplyRepository.findById(d.getSupplyId()).ifPresent(s -> {
                        s.setReservedQuantity(Math.max(0, s.getReservedQuantity() - d.getQuantity()));
                        supplyRepository.save(s);
                    });
                }
            }

            order.setStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);
            count++;
        }
        return count;
    }

    public record PreparedItem(
            String productType, UUID courseId, UUID supplyId, UUID childId,
            String productName, BigDecimal unitPrice, int quantity, BigDecimal commissionRate
    ) {}

    public record OrderDetailResponse(Order order, List<OrderDetail> details) {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutRequest {
        @com.fasterxml.jackson.annotation.JsonAlias({"useCart", "use_cart"})
        private Boolean useCart = true;

        public boolean isUseCart() {
            return useCart == null || useCart;
        }

        private List<CheckoutItem> items;

        @com.fasterxml.jackson.annotation.JsonAlias({"recipientName", "recipient_name"})
        private String recipientName;

        @com.fasterxml.jackson.annotation.JsonAlias({"recipientPhone", "recipient_phone", "phone"})
        private String recipientPhone;

        @com.fasterxml.jackson.annotation.JsonAlias({"shippingAddress", "shipping_address", "address"})
        private String shippingAddress;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutItem {
        @com.fasterxml.jackson.annotation.JsonAlias({"itemType", "productType", "item_type", "product_type", "type"})
        private String itemType;

        @com.fasterxml.jackson.annotation.JsonAlias({"courseId", "course_id"})
        private UUID courseId;

        @com.fasterxml.jackson.annotation.JsonAlias({"supplyId", "supply_id"})
        private UUID supplyId;

        @com.fasterxml.jackson.annotation.JsonAlias({"childId", "child_id"})
        private UUID childId;

        @com.fasterxml.jackson.annotation.JsonAlias({"quantity", "qty", "amount"})
        private int quantity;
    }
}
