package com.example.art_course.modules.cart;

import com.example.art_course.common.AppError;
import com.example.art_course.modules.cart.entity.Cart;
import com.example.art_course.modules.cart.entity.CartItem;
import com.example.art_course.modules.cart.repository.CartItemRepository;
import com.example.art_course.modules.cart.repository.CartRepository;
import com.example.art_course.modules.cart.service.CartService;
import com.example.art_course.modules.course.entity.Course;
import com.example.art_course.modules.course.repository.CourseRepository;
import com.example.art_course.modules.product.repository.SupplyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Giỏ hàng: khóa học vào giỏ chưa cần chọn bé, nhưng không thêm trùng")
class CartServiceTest {

    private final CartRepository carts = mock(CartRepository.class);
    private final CartItemRepository items = mock(CartItemRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CartService service = new CartService(
            carts, items, courses, mock(SupplyRepository.class), mock(EntityManager.class));
    private final UUID parentId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final Cart cart = Cart.builder().id(UUID.randomUUID()).parentId(parentId).build();

    private CartService.AddToCartInput course() {
        return CartService.AddToCartInput.builder().itemType("COURSE").courseId(courseId).build();
    }

    private void published() {
        Course c = new Course();
        c.setId(courseId);
        c.setStatus("PUBLISHED");
        when(courses.findById(courseId)).thenReturn(Optional.of(c));
        when(carts.findByParentId(parentId)).thenReturn(Optional.of(cart));
        when(items.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("không có childId: thêm được dòng khóa học với child null")
    void courseWithoutChild() {
        published();

        CartItem item = service.addToCart(parentId, course());

        assertNull(item.getChildId());
        assertEquals("COURSE", item.getProductType());
    }

    @Test
    @DisplayName("khóa học chưa chọn bé đã có trong giỏ → 409")
    void duplicateWithoutChild() {
        published();
        when(items.findByCartIdAndCourseIdAndChildId(cart.getId(), courseId, null))
                .thenReturn(Optional.of(new CartItem()));

        AppError e = assertThrows(AppError.class, () -> service.addToCart(parentId, course()));

        assertEquals("COURSE_ALREADY_IN_CART", e.getErrorCode());
    }
}
