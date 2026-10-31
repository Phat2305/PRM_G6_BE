package com.example.art_course.modules.cart;

import com.example.art_course.common.AppError;
import com.example.art_course.modules.cart.entity.Cart;
import com.example.art_course.modules.cart.entity.CartItem;
import com.example.art_course.modules.cart.repository.CartItemRepository;
import com.example.art_course.modules.cart.repository.CartRepository;
import com.example.art_course.modules.cart.service.CartService;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.supply.repository.SupplyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CartService service = new CartService(
            carts, items, courses, mock(SupplyRepository.class), entityManager, details);
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
        when(items.existsByCartIdAndCourseId(cart.getId(), courseId)).thenReturn(true);

        AppError e = assertThrows(AppError.class, () -> service.addToCart(parentId, course()));

        assertEquals("COURSE_ALREADY_IN_CART", e.getErrorCode());
    }

    // SHOP-04: đã có dòng khóa này cho bé A thì không thêm tiếp cho bé B, một khóa mua một lần.
    @Test
    @DisplayName("khóa đã có trong giỏ cho bé khác → 409")
    void duplicateForAnotherChild() {
        published();
        UUID childB = UUID.randomUUID();
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(java.util.List.of(childB), java.util.List.of());
        when(items.existsByCartIdAndCourseId(cart.getId(), courseId)).thenReturn(true);

        AppError e = assertThrows(AppError.class, () -> service.addToCart(parentId,
                CartService.AddToCartInput.builder().itemType("COURSE").courseId(courseId).childId(childB).build()));

        assertEquals("COURSE_ALREADY_IN_CART", e.getErrorCode());
    }

    // CUS-10: khóa làm riêng cho một yêu cầu không vào giỏ được.
    @Test
    @DisplayName("khóa customize → 400, không vào giỏ")
    void customizeCourseCannotBeAddedToCart() {
        Course c = new Course();
        c.setId(courseId);
        c.setStatus("PUBLISHED");
        c.setCustomizeRequestId(UUID.randomUUID());
        when(courses.findById(courseId)).thenReturn(Optional.of(c));
        when(carts.findByParentId(parentId)).thenReturn(Optional.of(cart));

        AppError e = assertThrows(AppError.class, () -> service.addToCart(parentId, course()));

        assertEquals("COURSE_UNAVAILABLE", e.getErrorCode());
        verify(items, never()).save(any());
    }

    @Test
    @DisplayName("phụ huynh đã trả tiền khóa này (chưa gán bé hoặc khóa customize) → 409, không vào giỏ")
    void parentAlreadyOwnsCourse() {
        published();
        when(details.parentOwnsCourse(parentId, courseId)).thenReturn(true);

        AppError e = assertThrows(AppError.class, () -> service.addToCart(parentId, course()));

        assertEquals("COURSE_ALREADY_OWNED", e.getErrorCode());
        verify(items, never()).save(any());
    }
}
