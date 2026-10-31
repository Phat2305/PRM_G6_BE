package com.example.art_course.modules.cart.service;

import com.example.art_course.common.AppError;
import com.example.art_course.modules.cart.entity.Cart;
import com.example.art_course.modules.cart.entity.CartItem;
import com.example.art_course.modules.cart.repository.CartItemRepository;
import com.example.art_course.modules.cart.repository.CartRepository;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CourseRepository courseRepository;
    private final SupplyRepository supplyRepository;
    private final EntityManager entityManager;
    private final OrderDetailRepository orderDetailRepository;

    @Transactional
    public Cart getOrCreateCart(UUID parentId) {
        return cartRepository.findByParentId(parentId)
                .orElseGet(() -> cartRepository.save(Cart.builder().parentId(parentId).build()));
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(UUID parentId) {
        Cart cart = getOrCreateCart(parentId);
        return cartItemRepository.findByCartId(cart.getId());
    }

    @Transactional
    public CartItem addToCart(UUID parentId, AddToCartInput input) {
        Cart cart = getOrCreateCart(parentId);

        if ("COURSE".equalsIgnoreCase(input.getItemType())) {
            if (input.getCourseId() == null) {
                throw new AppError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Khóa học yêu cầu courseId");
            }

            // Khóa học vào giỏ chưa cần chọn bé: phụ huynh gán sau khi mua (assign-child), nên childId có thể null.
            if (input.getChildId() != null) {
                // 1. Bé phải thuộc phụ huynh này (ACC-04)
                String childSql = "SELECT user_id FROM child_profiles WHERE parent_id = :parentId AND user_id = :childId";
                List<?> children = entityManager.createNativeQuery(childSql)
                        .setParameter("parentId", parentId)
                        .setParameter("childId", input.getChildId())
                        .getResultList();

                if (children.isEmpty()) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "NO_VALID_CHILD", "Phụ huynh phải có hồ sơ con hợp lệ để mua khóa học (ACC-04)");
                }
            }

            // 2. Kiểm tra khóa học tồn tại và PUBLISHED
            Course course = courseRepository.findById(input.getCourseId())
                    .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học"));

            if (!"PUBLISHED".equalsIgnoreCase(course.getStatus()) || course.getCustomizeRequestId() != null) {
                throw new AppError(HttpStatus.BAD_REQUEST, "COURSE_UNAVAILABLE", "Khóa học chưa được công khai");
            }

            // 3. Chặn mua trùng (SHOP-04): phụ huynh đã trả tiền khóa này thì gán thêm bé ở đơn cũ, không mua lại
            if (orderDetailRepository.parentOwnsCourse(parentId, input.getCourseId())) {
                throw new AppError(HttpStatus.CONFLICT, "COURSE_ALREADY_OWNED", "Bạn đã mua khóa học này, hãy kích hoạt cho bé ở đơn hàng (SHOP-04)");
            }
            if (input.getChildId() != null) {
                String enrollSql = "SELECT 1 FROM child_enroll WHERE child_id = :childId AND course_id = :courseId AND LOWER(status) = 'active'";
                List<?> enrolled = entityManager.createNativeQuery(enrollSql)
                        .setParameter("childId", input.getChildId())
                        .setParameter("courseId", input.getCourseId())
                        .getResultList();

                if (!enrolled.isEmpty()) {
                    throw new AppError(HttpStatus.CONFLICT, "COURSE_ALREADY_OWNED", "Bé đã sở hữu khóa học này (SHOP-04)");
                }
            }

            // Một khóa chỉ một dòng trong giỏ, bất kể bé (mua một lần, gán nhiều bé sau)
            if (cartItemRepository.existsByCartIdAndCourseId(cart.getId(), input.getCourseId())) {
                throw new AppError(HttpStatus.CONFLICT, "COURSE_ALREADY_IN_CART", "Khóa học này đã có trong giỏ hàng (SHOP-04)");
            }

            CartItem item = CartItem.builder()
                    .cartId(cart.getId())
                    .productType("COURSE")
                    .courseId(input.getCourseId())
                    .childId(input.getChildId())
                    .quantity(1)
                    .build();
            return cartItemRepository.save(item);

        } else if ("SUPPLY".equalsIgnoreCase(input.getItemType())) {
            if (input.getSupplyId() == null) {
                throw new AppError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Dụng cụ yêu cầu supplyId");
            }

            Supply supply = supplyRepository.findById(input.getSupplyId())
                    .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "SUPPLY_NOT_FOUND", "Không tìm thấy dụng cụ vẽ"));

            // SHOP-02: chỉ dụng cụ đang bán (ACTIVE) mới vào giỏ, khớp danh mục /api/v1/supplies
            if ("OUT_OF_STOCK".equalsIgnoreCase(supply.getStatus())) {
                throw new AppError(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "Dụng cụ \"" + supply.getName() + "\" đã hết hàng");
            }
            if (!"ACTIVE".equalsIgnoreCase(supply.getStatus())) {
                throw new AppError(HttpStatus.BAD_REQUEST, "SUPPLY_UNAVAILABLE", "Dụng cụ vẽ hiện không được bán");
            }

            int qty = input.getQuantity() > 0 ? input.getQuantity() : 1;
            int available = supply.getAvailableStock();

            var existingOpt = cartItemRepository.findByCartIdAndSupplyId(cart.getId(), input.getSupplyId());
            if (existingOpt.isPresent()) {
                CartItem existing = existingOpt.get();
                int newQty = existing.getQuantity() + qty;
                if (available < newQty) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "Tồn kho không đủ (còn " + available + ")");
                }
                existing.setQuantity(newQty);
                existing.setUpdatedAt(Instant.now());
                return cartItemRepository.save(existing);
            } else {
                if (available < qty) {
                    throw new AppError(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "Tồn kho không đủ (còn " + available + ")");
                }
                CartItem item = CartItem.builder()
                        .cartId(cart.getId())
                        .productType("SUPPLY")
                        .supplyId(input.getSupplyId())
                        .quantity(qty)
                        .build();
                return cartItemRepository.save(item);
            }
        }

        throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_TYPE", "Loại sản phẩm không hợp lệ");
    }

    @Transactional
    public void removeItem(UUID parentId, UUID itemId) {
        Cart cart = getOrCreateCart(parentId);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Không tìm thấy mục trong giỏ"));

        if (!item.getCartId().equals(cart.getId())) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Mục này không thuộc giỏ hàng của bạn");
        }
        cartItemRepository.delete(item);
    }

    /**
     * Cập nhật số lượng mục trong giỏ: kiểm tra quyền sở hữu giỏ và tồn kho khả dụng.
     */
    @Transactional
    public CartItem updateItemQuantity(UUID parentId, UUID itemId, int quantity) {
        if (quantity <= 0) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Số lượng sản phẩm phải lớn hơn 0");
        }

        Cart cart = getOrCreateCart(parentId);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Không tìm thấy mục trong giỏ"));

        // 1. Kiểm tra quyền sở hữu giỏ hàng
        if (!item.getCartId().equals(cart.getId())) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Mục này không thuộc giỏ hàng của bạn");
        }

        // 2. Khóa học cho một bé chỉ được phép số lượng 1
        if ("COURSE".equalsIgnoreCase(item.getProductType())) {
            if (quantity != 1) {
                throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Khóa học cho mỗi bé chỉ có thể mua với số lượng 1");
            }
            return item;
        }

        // 3. Kiểm tra tồn kho khả dụng đối với dụng cụ vẽ
        if ("SUPPLY".equalsIgnoreCase(item.getProductType())) {
            Supply supply = supplyRepository.findById(item.getSupplyId())
                    .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "SUPPLY_NOT_FOUND", "Không tìm thấy dụng cụ vẽ"));

            int available = supply.getAvailableStock();
            if (available < quantity) {
                throw new AppError(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                        "Dụng cụ \"" + supply.getName() + "\" không đủ tồn kho (còn " + available + ", yêu cầu " + quantity + ")");
            }

            item.setQuantity(quantity);
            item.setUpdatedAt(Instant.now());
            return cartItemRepository.save(item);
        }

        throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_TYPE", "Loại sản phẩm không hợp lệ");
    }

    @Transactional
    public void clearCart(UUID cartId) {
        cartItemRepository.deleteByCartId(cartId);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddToCartInput {
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateQuantityInput {
        @com.fasterxml.jackson.annotation.JsonAlias({"quantity", "qty", "amount"})
        private int quantity;
    }
}
