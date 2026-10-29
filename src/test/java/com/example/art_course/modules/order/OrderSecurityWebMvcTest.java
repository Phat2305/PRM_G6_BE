package com.example.art_course.modules.order;

import com.example.art_course.common.AppError;
import com.example.art_course.common.GlobalExceptionHandler;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.config.JwtAuthFilter;
import com.example.art_course.config.SecurityConfig;
import com.example.art_course.modules.order.controller.OrderController;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.service.OrderService;
import com.example.art_course.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = OrderSecurityWebMvcTest.TestSecurityConfig.class)
@TestPropertySource(properties = {
        "JWT_SECRET=real-spring-security-test-jwt-secret-key-at-least-32-bytes"
})
@DisplayName("ORDER SECURITY TEST (Real Spring Security FilterChain + JwtAuthFilter)")
public class OrderSecurityWebMvcTest {

    private static final String TEST_JWT_SECRET = "real-spring-security-test-jwt-secret-key-at-least-32-bytes";

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
    static class TestSecurityConfig {
        @Bean
        public OrderService orderService() {
            return mock(OrderService.class);
        }

        @Bean
        public UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        public OrderController orderController(OrderService orderService, UserRepository userRepository) {
            return new OrderController(orderService, userRepository);
        }

        @Bean
        public com.example.art_course.modules.payment.service.PaymentService paymentService() {
            return mock(com.example.art_course.modules.payment.service.PaymentService.class);
        }

        @Bean
        public com.example.art_course.modules.payment.controller.PaymentController paymentController(
                com.example.art_course.modules.payment.service.PaymentService service, UserRepository users) {
            return new com.example.art_course.modules.payment.controller.PaymentController(service, users);
        }

        @Bean
        public com.example.art_course.video.service.VideoStorageService videoStorage() {
            return mock(com.example.art_course.video.service.VideoStorageService.class);
        }

        @Bean
        public com.example.art_course.video.controller.VideoController videoController(
                com.example.art_course.video.service.VideoStorageService storage) {
            return new com.example.art_course.video.controller.VideoController(storage,
                    mock(com.example.art_course.video.messaging.VideoProcessingPublisher.class),
                    mock(com.example.art_course.video.service.VideoLessonService.class));
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private UUID parentAId;
    private UUID parentBId;
    private UUID teacherId;
    private UUID childId;
    private UUID orderBId;

    @BeforeEach
    void setUp() {
        reset(orderService, userRepository);
        // Mọi user trong test mặc định ACTIVE; JwtAuthFilter tra trạng thái ở mỗi request
        var activeState = authState("ACTIVE", null);
        when(userRepository.findAuthStateById(any())).thenReturn(java.util.Optional.of(activeState));
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        parentAId = UUID.randomUUID();
        parentBId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        childId = UUID.randomUUID();
        orderBId = UUID.randomUUID();
    }

    private UserRepository.AuthState authState(String status, java.time.Instant tokensValidAfter) {
        UserRepository.AuthState s = mock(UserRepository.AuthState.class);
        when(s.getStatus()).thenReturn(status);
        when(s.getTokensValidAfter()).thenReturn(tokensValidAfter);
        return s;
    }

    private String createTokenIssuedAt(UUID userId, java.time.Instant issuedAt) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", "PARENT")
                .claim("type", "ACCESS")
                .issuedAt(Date.from(issuedAt))
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String createToken(UUID userId, String role, String type) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("userId", userId.toString())
                .claim("role", role)
                .claim("type", type)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void mockWebhookHasNoPublicRoute() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook/MOCK")
                .contentType(MediaType.APPLICATION_JSON).content("{\"signature\":\"anything\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/payments/webhook/MOCK")
                .header("Authorization", "Bearer " + createToken(parentAId, "PARENT", "ACCESS"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"signature\":\"anything\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(context.getBean(com.example.art_course.modules.payment.service.PaymentService.class));
    }

    @Test
    void onlyTeacherCanUploadGenericVideo() throws Exception {
        var storage = context.getBean(com.example.art_course.video.service.VideoStorageService.class);
        when(storage.store(any())).thenReturn(new com.example.art_course.video.service.VideoStorageService.StoredVideo("clip.mp4", "unused-test-path"));
        var file = new org.springframework.mock.web.MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1});
        for (String role : java.util.List.of("PARENT", "CHILD", "STAFF", "ADMIN")) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/videos/upload")
                    .file(file).header("Authorization", "Bearer " + createToken(UUID.randomUUID(), role, "ACCESS")))
                    .andExpect(status().isForbidden());
        }
        verify(storage, never()).store(any());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/videos/upload")
                .file(file).header("Authorization", "Bearer " + createToken(teacherId, "TEACHER", "ACCESS")))
                .andExpect(status().isAccepted());
    }

    @Test
    void expiredAndTamperedTokensAreRejectedByTheRealFilter() throws Exception {
        String expired = Jwts.builder().subject(parentAId.toString()).claim("role", "PARENT")
                .claim("type", "ACCESS").expiration(Date.from(Instant.now().minusSeconds(1)))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        mockMvc.perform(get("/api/v1/orders/my-orders").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        String tampered = createToken(parentAId, "PARENT", "ACCESS") + "bad";
        mockMvc.perform(get("/api/v1/orders/my-orders").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("0. Tài khoản bị khóa hoặc đã xóa: token còn hạn vẫn bị chặn 403 ACCOUNT_NOT_ACTIVE")
    void testLockedOrDeletedAccountBlockedImmediately() throws Exception {
        String token = createToken(parentAId, "PARENT", "ACCESS");

        var state1 = authState("LOCKED", null);
        when(userRepository.findAuthStateById(parentAId)).thenReturn(java.util.Optional.of(state1));
        mockMvc.perform(get("/api/v1/orders/my-orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_ACTIVE"));

        when(userRepository.findAuthStateById(parentAId)).thenReturn(java.util.Optional.empty());
        mockMvc.perform(get("/api/v1/orders/my-orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_ACTIVE"));

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("0b. Token cấp trước mốc thu hồi bị 401 TOKEN_REVOKED, token cấp sau mốc vẫn dùng được")
    void testRevokedTokenRejectedButNewTokenAccepted() throws Exception {
        java.time.Instant revokedAt = java.time.Instant.now().minusSeconds(60);
        var state2 = authState("ACTIVE", revokedAt);
        when(userRepository.findAuthStateById(parentAId)).thenReturn(java.util.Optional.of(state2));
        String oldToken = createTokenIssuedAt(parentAId, revokedAt.minusSeconds(30));
        String newToken = createTokenIssuedAt(parentAId, revokedAt.plusSeconds(30));

        mockMvc.perform(get("/api/v1/orders/my-orders").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_REVOKED"));

        // Token mới đi qua bộ lọc (không bị thu hồi); controller trả dữ liệu hoặc lỗi nghiệp vụ, nhưng không phải 401/403 của bộ lọc
        int code = mockMvc.perform(get("/api/v1/orders/my-orders").header("Authorization", "Bearer " + newToken))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(401, code);
        org.junit.jupiter.api.Assertions.assertNotEquals(403, code);
    }

    @Test
    @DisplayName("1. Không có JWT token gọi API đơn hàng bị Spring Security chặn (401 Unauthorized)")
    void testUnauthenticatedRequestBlocked() throws Exception {
        mockMvc.perform(get("/api/v1/orders/my-orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("2. Vai trò CHILD gọi API mua hàng bị JwtAuthFilter chặn 403 FORBIDDEN (BR-02, NFR-04)")
    void testChildRoleBlockedBySpringSecurity() throws Exception {
        String childToken = createToken(childId, "CHILD", "ACCESS");

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"useCart\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("3. Dùng REFRESH token gọi API bị JwtAuthFilter chặn 401 UNAUTHORIZED")
    void testRefreshTokenBlockedBySpringSecurity() throws Exception {
        String refreshToken = createToken(parentAId, "PARENT", "REFRESH");

        mockMvc.perform(get("/api/v1/orders/my-orders")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN_TYPE"));
    }

    @Test
    @DisplayName("4. Vai trò TEACHER gọi endpoint checkout của PARENT bị SecurityFilterChain chặn 403")
    void testTeacherCannotCallParentCheckoutEndpoint() throws Exception {
        String teacherToken = createToken(teacherId, "TEACHER", "ACCESS");

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"useCart\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("5. Vai trò PARENT gọi endpoint teacher-orders của TEACHER bị SecurityFilterChain chặn 403")
    void testParentCannotCallTeacherOrdersEndpoint() throws Exception {
        String parentToken = createToken(parentAId, "PARENT", "ACCESS");

        mockMvc.perform(get("/api/v1/orders/teacher-orders")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("6. Parent A không được đọc chi tiết đơn của Parent B (403 Forbidden)")
    void testParentACannotReadOrderOfParentB() throws Exception {
        String parentAToken = createToken(parentAId, "PARENT", "ACCESS");
        when(orderService.getOrderDetail(eq(orderBId), eq(parentAId), eq("PARENT")))
                .thenThrow(new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Đơn hàng này không thuộc về bạn"));

        mockMvc.perform(get("/api/v1/orders/" + orderBId)
                        .header("Authorization", "Bearer " + parentAToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Đơn hàng này không thuộc về bạn"));

        verify(orderService).getOrderDetail(eq(orderBId), eq(parentAId), eq("PARENT"));
    }

    @Test
    @DisplayName("7. Parent A không được hủy đơn hàng của Parent B (403 Forbidden)")
    void testParentACannotCancelOrderOfParentB() throws Exception {
        String parentAToken = createToken(parentAId, "PARENT", "ACCESS");
        doThrow(new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền hủy đơn hàng này"))
                .when(orderService).cancelOrder(eq(orderBId), eq(parentAId), eq("PARENT"));

        mockMvc.perform(post("/api/v1/orders/" + orderBId + "/cancel")
                        .header("Authorization", "Bearer " + parentAToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Bạn không có quyền hủy đơn hàng này"));

        verify(orderService).cancelOrder(eq(orderBId), eq(parentAId), eq("PARENT"));
    }

    @Test
    @DisplayName("8. Parent A đọc đơn của chính mình qua Spring Security thật thành công (200 OK)")
    void testParentACanReadOwnOrder() throws Exception {
        String parentAToken = createToken(parentAId, "PARENT", "ACCESS");
        UUID orderAId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderAId)
                .parentId(parentAId)
                .teacherId(teacherId)
                .totalAmount(BigDecimal.valueOf(250000))
                .shippingFee(BigDecimal.ZERO)
                .status(OrderStatus.PENDING_PAYMENT)
                .expiredAt(Instant.now().plusSeconds(900))
                .build();

        OrderService.OrderDetailResponse response = new OrderService.OrderDetailResponse(order, Collections.emptyList());
        when(orderService.getOrderDetail(eq(orderAId), eq(parentAId), eq("PARENT"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/" + orderAId)
                        .header("Authorization", "Bearer " + parentAToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.order.id").value(orderAId.toString()));
    }
}
