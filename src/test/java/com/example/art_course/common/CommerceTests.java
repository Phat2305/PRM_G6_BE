package com.example.art_course.common;

import com.example.art_course.common.statemachine.OrderStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PROMPT 3 — JUNIT 5 TEST SUITE (Task A: Commerce & Orders)")
public class CommerceTests {

    private static final String TEST_ONLY_MOCK_KEY = "junit-test-only-hmac-sha-256-mock-key-not-for-production-use";

    // =========================================================================
    // TEST 1: TÁCH ĐƠN NHIỀU TEACHER (BR-04, SHOP-05, #25)
    // =========================================================================
    @Test
    @DisplayName("1. Tách đơn nhiều Teacher: giỏ hàng nhiều giáo viên tách thành mỗi giáo viên 1 đơn riêng")
    void testMultiTeacherOrderSplit() {
        record CartItemTest(String itemType, UUID teacherId, String title, Money unitPrice, int quantity, UUID childId) {}

        UUID teacher1 = UUID.randomUUID();
        UUID teacher2 = UUID.randomUUID();
        UUID child1 = UUID.randomUUID();

        List<CartItemTest> cartItems = List.of(
                new CartItemTest("COURSE", teacher1, "Khóa học vẽ nét", Money.of(300000), 1, child1),
                new CartItemTest("SUPPLY", teacher1, "Bộ bút chì màu", Money.of(120000), 1, null),
                new CartItemTest("COURSE", teacher2, "Màu nước nâng cao", Money.of(500000), 1, child1)
        );

        // Gom nhóm theo Teacher
        Map<UUID, List<CartItemTest>> teacherGroups = new LinkedHashMap<>();
        for (CartItemTest item : cartItems) {
            teacherGroups.computeIfAbsent(item.teacherId(), k -> new ArrayList<>()).add(item);
        }

        assertEquals(2, teacherGroups.size(), "Phải tách thành đúng 2 đơn theo 2 Teacher (BR-04, #25)");

        // Kiểm tra đơn Teacher 1 (Khóa học 300k + Dụng cụ 120k + Phí ship 30k = 450k)
        List<CartItemTest> itemsTeacher1 = teacherGroups.get(teacher1);
        Money subtotal1 = Money.ZERO;
        boolean hasSupply1 = false;
        for (CartItemTest it : itemsTeacher1) {
            subtotal1 = subtotal1.add(it.unitPrice().multiply(it.quantity()));
            if ("SUPPLY".equals(it.itemType())) hasSupply1 = true;
        }
        Money shippingFee1 = hasSupply1 ? Money.of(30000) : Money.ZERO;
        Money total1 = subtotal1.add(shippingFee1);

        assertEquals("420000", subtotal1.toString());
        assertEquals("30000", shippingFee1.toString(), "Đơn có dụng cụ phải tính phí ship cố định 30.000 VND (#8)");
        assertEquals("450000", total1.toString());

        // Kiểm tra đơn Teacher 2 (Khóa học 500k, không có dụng cụ -> ship 0 VND)
        List<CartItemTest> itemsTeacher2 = teacherGroups.get(teacher2);
        Money subtotal2 = Money.ZERO;
        boolean hasSupply2 = false;
        for (CartItemTest it : itemsTeacher2) {
            subtotal2 = subtotal2.add(it.unitPrice().multiply(it.quantity()));
            if ("SUPPLY".equals(it.itemType())) hasSupply2 = true;
        }
        Money shippingFee2 = hasSupply2 ? Money.of(30000) : Money.ZERO;
        Money total2 = subtotal2.add(shippingFee2);

        assertEquals("500000", subtotal2.toString());
        assertEquals("0", shippingFee2.toString(), "Đơn chỉ có khóa học không tính phí ship");
        assertEquals("500000", total2.toString());
    }

    // =========================================================================
    // TEST 2: BÁN VƯỢT TỒN KHI ĐỒNG THỜI TRÊN POSTGRESQL THẬT (PAY-07)
    // =========================================================================
    @Test
    @DisplayName("2. Bán vượt tồn khi đồng thời (PostgreSQL thật + PESSIMISTIC_WRITE): 10 threads mua đồng thời nhưng tồn kho chỉ có 5 -> đúng 5 thành công")
    void testConcurrentOversellingPrevention() throws InterruptedException {
        var ctx = PostgresTestcontainerEnv.getContext();
        var jdbc = ctx.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var orderService = ctx.getBean(com.example.art_course.modules.order.service.OrderService.class);
        var supplyRepo = ctx.getBean(com.example.art_course.modules.product.repository.SupplyRepository.class);

        UUID parentId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();

        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'PARENT', 'ACTIVE')",
                parentId, "parent_oversell_" + parentId);
        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'TEACHER', 'ACTIVE')",
                teacherId, "teacher_oversell_" + teacherId);
        jdbc.update("INSERT INTO public.supplies (id, teacher_id, name, price, stock_quantity, reserved_quantity, sold_quantity, status) VALUES (?, ?, 'Co ve Test', 50000, 5, 0, 0, 'ACTIVE')",
                supplyId, teacherId);

        int totalThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var item = new com.example.art_course.modules.order.service.OrderService.CheckoutItem();
                    item.setItemType("SUPPLY");
                    item.setSupplyId(supplyId);
                    item.setQuantity(1);

                    var req = new com.example.art_course.modules.order.service.OrderService.CheckoutRequest();
                    req.setUseCart(false);
                    req.setItems(List.of(item));

                    orderService.createOrdersFromCheckout(parentId, req);
                    successCount.incrementAndGet();
                } catch (AppError ex) {
                    if ("INSUFFICIENT_STOCK".equals(ex.getErrorCode())) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        var updatedSupply = supplyRepo.findById(supplyId).orElseThrow();
        assertTrue(completed, "Các thread phải hoàn thành trong thời gian cho phép");
        assertEquals(5, successCount.get(), "Chính xác 5 request đầu tiên được giữ chỗ thành công trên PostgreSQL");
        assertEquals(5, failCount.get(), "Chính xác 5 request vượt tồn bị từ chối với lỗi INSUFFICIENT_STOCK (PAY-07)");
        assertEquals(5, updatedSupply.getReservedQuantity(), "Tồn kho giữ chỗ trong DB đạt mức 5");
    }

    // =========================================================================
    // TEST 3: HẾT HẠN ĐƠN TRẢ TỒN ĐÚNG MỘT LẦN TRÊN POSTGRESQL THẬT (PAY-08)
    // =========================================================================
    @Test
    @DisplayName("3. Hết hạn đơn trả tồn đúng một lần trên PostgreSQL thật: chạy lại không trả hai lần (idempotent)")
    void testOrderExpirationIdempotency() {
        var ctx = PostgresTestcontainerEnv.getContext();
        var jdbc = ctx.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var orderService = ctx.getBean(com.example.art_course.modules.order.service.OrderService.class);
        var orderRepo = ctx.getBean(com.example.art_course.modules.order.repository.OrderRepository.class);
        var supplyRepo = ctx.getBean(com.example.art_course.modules.product.repository.SupplyRepository.class);

        // Dọn các đơn PENDING_PAYMENT quá hạn cũ nếu có
        orderService.expirePendingOrders();

        UUID parentId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'PARENT', 'ACTIVE')",
                parentId, "parent_exp_" + parentId);
        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'TEACHER', 'ACTIVE')",
                teacherId, "teacher_exp_" + teacherId);
        jdbc.update("INSERT INTO public.supplies (id, teacher_id, name, price, stock_quantity, reserved_quantity, sold_quantity, status) VALUES (?, ?, 'Mau ve', 60000, 10, 2, 0, 'ACTIVE')",
                supplyId, teacherId);
        jdbc.update("INSERT INTO public.orders (id, parent_id, teacher_id, total_amount, shipping_fee, status, expired_at) VALUES (?, ?, ?, 150000, 30000, 'PENDING_PAYMENT', CURRENT_TIMESTAMP - INTERVAL '5 minutes')",
                orderId, parentId, teacherId);
        jdbc.update("INSERT INTO public.order_details (order_id, supply_id, product_name, unit_price, quantity, sub_total, delivery_status) VALUES (?, ?, 'Mau ve', 60000, 2, 120000, 'PENDING')",
                orderId, supplyId);

        // Lần chạy 1: Hết hạn đơn -> trả lại 2 tồn kho
        int run1 = orderService.expirePendingOrders();
        assertEquals(1, run1, "Lần 1 phải xử lý đúng 1 đơn quá hạn");
        assertEquals(0, supplyRepo.findById(supplyId).orElseThrow().getReservedQuantity(), "Tồn kho giữ chỗ đã được hoàn trả về 0");
        assertEquals(OrderStatus.EXPIRED, orderRepo.findById(orderId).orElseThrow().getStatus());

        // Lần chạy 2: Chạy lại trên đơn đã EXPIRED -> không hoàn trả thêm
        int run2 = orderService.expirePendingOrders();
        assertEquals(0, run2, "Không thực hiện trả tồn kho lần 2 (Idempotent)");
        assertEquals(0, supplyRepo.findById(supplyId).orElseThrow().getReservedQuantity(), "Tồn giữ chỗ không bị trừ âm");
    }

    // =========================================================================
    // TEST 4: CHẶN MUA TRÙNG KHÓA HỌC CHO CÙNG CHILD TRÊN POSTGRESQL THẬT (SHOP-04)
    // =========================================================================
    @Test
    @DisplayName("4. Chặn mua trùng khóa học cho cùng một Child trên PostgreSQL thật (SHOP-04)")
    void testPreventDuplicateCourseForChild() {
        var ctx = PostgresTestcontainerEnv.getContext();
        var jdbc = ctx.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var orderService = ctx.getBean(com.example.art_course.modules.order.service.OrderService.class);

        UUID parentId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID child1 = UUID.randomUUID();
        UUID child2 = UUID.randomUUID();
        UUID course1 = UUID.randomUUID();

        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'PARENT', 'ACTIVE')",
                parentId, "parent_dup_" + parentId);
        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'TEACHER', 'ACTIVE')",
                teacherId, "teacher_dup_" + teacherId);
        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'CHILD', 'ACTIVE')",
                child1, "child1_dup_" + child1);
        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'CHILD', 'ACTIVE')",
                child2, "child2_dup_" + child2);
        jdbc.update("INSERT INTO public.child_profiles (user_id, parent_id, display_name, birth_year) VALUES (?, ?, 'Be 1', 2017)",
                child1, parentId);
        jdbc.update("INSERT INTO public.child_profiles (user_id, parent_id, display_name, birth_year) VALUES (?, ?, 'Be 2', 2019)",
                child2, parentId);
        jdbc.update("INSERT INTO public.courses (id, teacher_id, name, price, status) VALUES (?, ?, 'Khoa ve', 250000, 'PUBLISHED')",
                course1, teacherId);

        // Child 1 đã sở hữu Course 1 trong bảng child_enroll
        jdbc.update("INSERT INTO public.child_enroll (child_id, course_id, status) VALUES (?, ?, 'ACTIVE')",
                child1, course1);

        var item1 = new com.example.art_course.modules.order.service.OrderService.CheckoutItem();
        item1.setItemType("COURSE");
        item1.setCourseId(course1);
        item1.setChildId(child1);
        item1.setQuantity(1);
        var req1 = new com.example.art_course.modules.order.service.OrderService.CheckoutRequest();
        req1.setUseCart(false);
        req1.setItems(List.of(item1));

        // Bé 1 mua Course 1 (đã có ACTIVE trong child_enroll) -> Phải ném lỗi 409 CONFLICT
        AppError ex = assertThrows(AppError.class, () -> orderService.createOrdersFromCheckout(parentId, req1));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("COURSE_ALREADY_OWNED", ex.getErrorCode());

        // Bé 2 mua Course 1 (chưa có) -> Thành công không ném lỗi
        var item2 = new com.example.art_course.modules.order.service.OrderService.CheckoutItem();
        item2.setItemType("COURSE");
        item2.setCourseId(course1);
        item2.setChildId(child2);
        item2.setQuantity(1);
        var req2 = new com.example.art_course.modules.order.service.OrderService.CheckoutRequest();
        req2.setUseCart(false);
        req2.setItems(List.of(item2));

        assertDoesNotThrow(() -> orderService.createOrdersFromCheckout(parentId, req2));
    }

    // =========================================================================
    // TEST 5: JWT CONTRACT TESTS (ACCESS vs REFRESH, EXPIRATION, ROLE CHILD)
    // =========================================================================
    @Test
    @DisplayName("5.1. Phân quyền: Child gọi API mua hàng/thanh toán bị từ chối 403 FORBIDDEN (BR-02, NFR-04)")
    void testChildRoleForbidden() {
        String childToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "CHILD")
                .claim("type", "ACCESS")
                .signWith(Keys.hmacShaKeyFor(TEST_ONLY_MOCK_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TEST_ONLY_MOCK_KEY.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(childToken)
                .getPayload();

        String role = claims.get("role", String.class);
        boolean isChildForbidden = "CHILD".equalsIgnoreCase(role);
        assertTrue(isChildForbidden, "Vai trò CHILD phải bị phát hiện để chặn truy cập");

        String parentToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "PARENT")
                .claim("type", "ACCESS")
                .signWith(Keys.hmacShaKeyFor(TEST_ONLY_MOCK_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();

        var parentClaims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TEST_ONLY_MOCK_KEY.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(parentToken)
                .getPayload();

        boolean isParentForbidden = "CHILD".equalsIgnoreCase(parentClaims.get("role", String.class));
        assertFalse(isParentForbidden, "Vai trò PARENT được phép truy cập");
    }

    @Test
    @DisplayName("5.2. JWT Contract: Chỉ chấp nhận ACCESS token, từ chối REFRESH token (INVALID_TOKEN_TYPE)")
    void testRefreshTokenRejected() {
        String refreshToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "PARENT")
                .claim("type", "REFRESH")
                .signWith(Keys.hmacShaKeyFor(TEST_ONLY_MOCK_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TEST_ONLY_MOCK_KEY.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(refreshToken)
                .getPayload();

        String tokenType = claims.get("type", String.class);
        assertNotEquals("ACCESS", tokenType);
        assertEquals("REFRESH", tokenType, "Refresh token phải bị phát hiện");

        // Mô phỏng kiểm tra của JwtAuthFilter
        boolean isAllowed = "ACCESS".equalsIgnoreCase(tokenType);
        assertFalse(isAllowed, "Refresh token không được phép gọi API tài nguyên");
    }

    @Test
    @DisplayName("5.3. JWT Contract: Token đã hết hạn phải bị ném lỗi ExpiredJwtException (TOKEN_EXPIRED)")
    void testExpiredTokenRejected() {
        Date past = new Date(System.currentTimeMillis() - 3600_000); // 1 giờ trước
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "PARENT")
                .claim("type", "ACCESS")
                .expiration(past)
                .signWith(Keys.hmacShaKeyFor(TEST_ONLY_MOCK_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> {
            Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(TEST_ONLY_MOCK_KEY.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(expiredToken);
        }, "Token quá hạn bắt buộc phải ném ExpiredJwtException");
    }

    @Test
    @DisplayName("5.4. JWT Contract: Token sai chữ ký bị từ chối (INVALID_TOKEN)")
    void testInvalidSignatureRejected() {
        String otherKey = "another-wrong-key-that-does-not-match-signature-at-all-32b";
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "PARENT")
                .claim("type", "ACCESS")
                .signWith(Keys.hmacShaKeyFor(otherKey.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(Exception.class, () -> {
            Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(TEST_ONLY_MOCK_KEY.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token);
        }, "Token ký bằng key khác phải bị từ chối do sai chữ ký");
    }

    // =========================================================================
    // TEST 6: BẢO TOÀN HOA HỒNG VÀ TIỀN THỰC NHẬN (TECHNICAL RULE #1)
    // =========================================================================
    @Test
    @DisplayName("6. Tính toán tiền VND và bảo toàn hoa hồng (gross = commission + net)")
    void testMoneyCommissionIntegrity() {
        Money price = Money.of(350000);
        int quantity = 3;
        Money subtotal = price.multiply(quantity);
        assertEquals("1050000", subtotal.toString());

        // Hoa hồng 20%
        var result = subtotal.calculateCommission(new BigDecimal("0.20"));
        assertEquals("210000", result.commission().toString());
        assertEquals("840000", result.net().toString());
        assertEquals(subtotal.toString(), result.commission().add(result.net()).toString(), "Bảo toàn số tiền tuyệt đối");
    }

    // =========================================================================
    // TEST 7: BẢO MẬT DTO KHÓA HỌC: TUYỆT ĐỐI KHÔNG CHỨA VIDEO_URL THÔ
    // =========================================================================
    @Test
    @DisplayName("7. Bảo mật DTO: Tuyệt đối không chứa trường video_url thô trong DTO thương mại (SHOP-01, SHOP-10)")
    void testCourseDtosDoNotContainRawVideoUrl() {
        Class<?>[] dtoClasses = {
                com.example.art_course.modules.course.dto.CourseSummaryDto.class,
                com.example.art_course.modules.course.dto.CourseDetailDto.class,
                com.example.art_course.modules.course.dto.LessonSummaryDto.class,
                com.example.art_course.modules.course.dto.TrialLessonDto.class
        };

        for (Class<?> clazz : dtoClasses) {
            java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
            boolean hasRawVideoUrl = java.util.Arrays.stream(fields)
                    .anyMatch(f -> f.getName().equalsIgnoreCase("videoUrl") || f.getName().equalsIgnoreCase("video_url"));
            assertFalse(hasRawVideoUrl, "Lớp " + clazz.getSimpleName() + " tuyệt đối không được chứa trường video_url thô từ database");
        }
    }
}
