package com.example.art_course.modules.order.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.Money;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.modules.cart.entity.Cart;
import com.example.art_course.modules.cart.entity.CartItem;
import com.example.art_course.modules.cart.repository.CartItemRepository;
import com.example.art_course.modules.cart.repository.CartRepository;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
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
    private final com.example.art_course.common.TeacherNames teacherNames;
    private final com.example.art_course.child.repository.ChildRepository childRepository;
    private final com.example.art_course.modules.payment.service.EnrollmentGrantService enrollmentGrantService;
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
            itemsToProcess = new ArrayList<>(cartItemRepository.findByCartId(cartId));
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

        // PAY-07: các request khóa dụng cụ theo cùng thứ tự để tránh deadlock.
        itemsToProcess.sort(Comparator.comparing(i -> i.getSupplyId() == null ? "" : i.getSupplyId().toString()));

        // Nhóm theo Teacher
        Map<UUID, List<PreparedItem>> teacherGroups = new LinkedHashMap<>();

        BigDecimal commCourse = configService.getCommissionRateCourse();
        BigDecimal commSupply = configService.getCommissionRateSupply();

        for (CartItem item : itemsToProcess) {
            if ("COURSE".equalsIgnoreCase(item.getProductType())) {
                if (item.getCourseId() == null) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Khóa học phải có courseId");
                }

                // Bé có thể chọn sau khi thanh toán (assignChild); có chọn sẵn thì chặn mua trùng (SHOP-04)
                if (item.getChildId() != null) {
                    requireOwnedChild(parentId, item.getChildId());
                    requireChildNotEnrolled(item.getChildId(), item.getCourseId());
                }

                Course course = courseRepository.findById(item.getCourseId())
                        .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học"));

                if (!"PUBLISHED".equalsIgnoreCase(course.getStatus()) || course.getCustomizeRequestId() != null) {
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

                // SHOP-05: dụng cụ đã ngừng bán không được đặt dù còn nằm trong giỏ
                if ("OUT_OF_STOCK".equalsIgnoreCase(supply.getStatus())) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "Dụng cụ \"" + supply.getName() + "\" đã hết hàng");
                }
                if (!"ACTIVE".equalsIgnoreCase(supply.getStatus())) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "SUPPLY_UNAVAILABLE", "Dụng cụ \"" + supply.getName() + "\" hiện không được bán");
                }

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

            if (hasSupply) validateRecipient(request);
            Order order = Order.builder()
                    .recipientName(hasSupply ? request.getRecipientName().trim() : null)
                    .recipientPhone(hasSupply ? request.getRecipientPhone().replace(" ", "").trim() : null)
                    .shippingAddress(hasSupply ? request.getShippingAddress().trim() : null)
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

        withTeacherNames(createdOrders);
        return createdOrders;
    }

    // SHOP-03: nhận hàng vật lý phải đủ tên, SĐT và địa chỉ trước khi tạo đơn.
    private void validateRecipient(CheckoutRequest request) {
        String name = request.getRecipientName();
        String phone = request.getRecipientPhone();
        String address = request.getShippingAddress();
        if (name == null || name.isBlank() || name.trim().length() > 255
                || phone == null || !phone.replace(" ", "").trim().matches("^(0|\\+84)\\d{9}$")
                || address == null || address.isBlank() || address.trim().length() > 500) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_RECIPIENT", "Cần tên, số điện thoại hợp lệ và địa chỉ giao hàng (tối đa 500 ký tự)");
        }
    }

    private void requireOwnedChild(UUID parentId, UUID childId) {
        childRepository.findByUserIdAndParentId(childId, parentId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "CHILD_NOT_FOUND", "Không tìm thấy bé của bạn"));
    }

    private void requireChildNotEnrolled(UUID childId, UUID courseId) {
        String enrollSql = "SELECT 1 FROM child_enroll WHERE child_id = :childId AND course_id = :courseId AND LOWER(status) = 'active'";
        List<?> enrolled = entityManager.createNativeQuery(enrollSql)
                .setParameter("childId", childId)
                .setParameter("courseId", courseId)
                .getResultList();
        if (!enrolled.isEmpty()) {
            throw new AppError(HttpStatus.CONFLICT, "COURSE_ALREADY_OWNED", "Bé đã sở hữu khóa học này (SHOP-04)");
        }
    }

    /**
     * Chọn bé cho khóa học đã mua mà chưa gán bé; sau thanh toán, không ràng buộc thời điểm.
     * Cấp quyền học cho bé ngay (SHOP-04, LRN).
     */
    @Transactional
    public OrderDetail assignChild(UUID orderId, UUID detailId, UUID parentId, UUID childId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
        if (!order.getParentId().equals(parentId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Đơn hàng này không thuộc về bạn");
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.EXPIRED) {
            throw new AppError(HttpStatus.BAD_REQUEST, "ORDER_NOT_PAID", "Đơn hàng chưa thanh toán");
        }
        OrderDetail detail = orderDetailRepository.findById(detailId)
                .filter(d -> d.getOrderId().equals(orderId))
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ORDER_DETAIL_NOT_FOUND", "Không tìm thấy dòng đơn hàng"));
        if (!"COURSE".equalsIgnoreCase(detail.getProductType())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "NOT_A_COURSE", "Chỉ khóa học mới gán cho bé");
        }
        if (detail.getDeliveryStatus() == DeliveryStatus.REFUNDED) {
            throw new AppError(HttpStatus.CONFLICT, "ITEM_REFUNDED", "Khóa học đã được hoàn tiền");
        }
        if (detail.getChildId() != null) {
            throw new AppError(HttpStatus.CONFLICT, "CHILD_ALREADY_ASSIGNED", "Khóa học đã được gán cho bé");
        }
        requireOwnedChild(parentId, childId);
        requireChildNotEnrolled(childId, detail.getCourseId());

        detail.setChildId(childId);
        orderDetailRepository.save(detail);
        enrollmentGrantService.grantAccessWithRetry(childId, detail.getCourseId(), orderId);
        return detail;
    }

    /**
     * Xác định phí vận chuyển cho đơn hàng của Teacher:
     * Phí ship riêng của Teacher (teacher_profiles.shipping_fee); chưa đặt thì đọc system_config (shipping_fee_default).
     */
    public Money resolveShippingFee(UUID teacherId) {
        Long own = teacherNames.shippingFeeOf(teacherId);
        return own != null ? Money.of(own) : configService.getDefaultShippingFee();
    }

    @Transactional(readOnly = true)
    public List<Order> getParentOrders(UUID parentId) {
        return withTeacherNames(orderRepository.findByParentIdOrderByCreatedAtDesc(parentId));
    }

    @Transactional(readOnly = true)
    public List<Order> getTeacherOrders(UUID teacherId) {
        return withTeacherNames(orderRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId));
    }

    private List<Order> withTeacherNames(List<Order> orders) {
        Map<UUID, String> names = teacherNames.byIds(orders.stream().map(Order::getTeacherId).distinct().toList());
        orders.forEach(o -> o.setTeacherName(names.get(o.getTeacherId())));
        return orders;
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

        order.setTeacherName(teacherNames.byId(order.getTeacherId()));
        List<OrderDetail> details = orderDetailRepository.findByOrderId(orderId);
        return new OrderDetailResponse(order, details);
    }

    /**
     * Hủy đơn hàng đang chờ thanh toán: giải phóng tồn kho giữ chỗ (BR-11).
     * Cho phép phụ huynh chủ sở hữu đơn hàng HOẶC STAFF/ADMIN hủy.
     */
    @Transactional
    public void cancelOrder(UUID orderId, UUID userId, String role) {
        Order order = orderRepository.findByIdForUpdate(orderId)
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
        List<OrderDetail> details = new ArrayList<>(orderDetailRepository.findByOrderIdAndSupplyIdIsNotNull(orderId));
        details.sort(Comparator.comparing(d -> d.getSupplyId().toString()));
        for (OrderDetail d : details) {
            if (d.getSupplyId() != null) {
                supplyRepository.findByIdForUpdate(d.getSupplyId()).ifPresent(s -> {
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
        List<UUID> expiredOrders = orderRepository.findExpiredIds(
                OrderStatus.PENDING_PAYMENT, Instant.now()
        );

        int count = 0;
        for (UUID orderId : expiredOrders) {
            Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getExpiredAt() == null || order.getExpiredAt().isAfter(Instant.now())) continue;
            // Đơn customize do CustomizeQuoteExpirationJob xử lý vì phải mở lại yêu cầu và báo giá (CUS-18).
            if (!orderDetailRepository.findByOrderIdAndCustomizeRequestIdIsNotNull(order.getId()).isEmpty()) {
                continue;
            }
            List<OrderDetail> details = new ArrayList<>(orderDetailRepository.findByOrderIdAndSupplyIdIsNotNull(order.getId()));
            details.sort(Comparator.comparing(d -> d.getSupplyId().toString()));
            for (OrderDetail d : details) {
                if (d.getSupplyId() != null) {
                    supplyRepository.findByIdForUpdate(d.getSupplyId()).ifPresent(s -> {
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
