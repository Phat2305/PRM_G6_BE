package com.example.art_course.modules.order;

import com.example.art_course.common.AppError;
import com.example.art_course.common.GlobalExceptionHandler;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.modules.order.controller.OrderController;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ORDER SECURITY TEST (MockMvc WebMvc, No JPA, No DB)")
public class OrderSecurityWebMvcTest {

    private MockMvc mockMvc;
    private OrderService orderService;

    private UUID parentAId;
    private UUID parentBId;
    private UUID orderBId;
    private UsernamePasswordAuthenticationToken authParentA;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        OrderController orderController = new OrderController(orderService);

        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        parentAId = UUID.randomUUID();
        parentBId = UUID.randomUUID();
        orderBId = UUID.randomUUID();

        authParentA = new UsernamePasswordAuthenticationToken(
                parentAId, null, List.of(new SimpleGrantedAuthority("ROLE_PARENT"))
        );
    }

    @Test
    @DisplayName("1. Parent A không được đọc chi tiết đơn của Parent B (403 Forbidden)")
    void testParentACannotReadOrderOfParentB() throws Exception {
        when(orderService.getOrderDetail(eq(orderBId), eq(parentAId), eq("PARENT")))
                .thenThrow(new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Đơn hàng này không thuộc về bạn"));

        mockMvc.perform(get("/api/v1/orders/" + orderBId)
                        .principal(authParentA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Đơn hàng này không thuộc về bạn"));

        verify(orderService).getOrderDetail(eq(orderBId), eq(parentAId), eq("PARENT"));
    }

    @Test
    @DisplayName("2. Parent A không được hủy đơn hàng của Parent B (403 Forbidden)")
    void testParentACannotCancelOrderOfParentB() throws Exception {
        doThrow(new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền hủy đơn hàng này"))
                .when(orderService).cancelOrder(eq(orderBId), eq(parentAId), eq("PARENT"));

        mockMvc.perform(post("/api/v1/orders/" + orderBId + "/cancel")
                        .principal(authParentA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Bạn không có quyền hủy đơn hàng này"));

        verify(orderService).cancelOrder(eq(orderBId), eq(parentAId), eq("PARENT"));
    }

    @Test
    @DisplayName("3. Parent A đọc đơn của chính mình thành công (200 OK)")
    void testParentACanReadOwnOrder() throws Exception {
        UUID orderAId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderAId)
                .parentId(parentAId)
                .teacherId(UUID.randomUUID())
                .totalAmount(BigDecimal.valueOf(250000))
                .shippingFee(BigDecimal.ZERO)
                .status(OrderStatus.PENDING_PAYMENT)
                .expiredAt(Instant.now().plusSeconds(900))
                .build();

        OrderService.OrderDetailResponse response = new OrderService.OrderDetailResponse(order, Collections.emptyList());
        when(orderService.getOrderDetail(eq(orderAId), eq(parentAId), eq("PARENT"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/" + orderAId)
                        .principal(authParentA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.order.id").value(orderAId.toString()));
    }
}
