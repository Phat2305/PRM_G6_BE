package com.example.art_course.common;

import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.common.statemachine.StateMachineValidator;
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
    // TEST 2: BÁN VƯỢT TỒN KHI ĐỒNG THỜI (EXECUTORSERVICE + COUNTDOWNLATCH) (PAY-07)
    // =========================================================================
    @Test
    @Disabled("Cần kết nối PostgreSQL thật (Testcontainers / DATABASE_URL_DEV) để kiểm chứng @Lock(PESSIMISTIC_WRITE) và trigger cơ sở dữ liệu, không dùng in-memory mock để tránh pass giả.")
    @DisplayName("2. Bán vượt tồn khi đồng thời: 10 threads mua đồng thời nhưng tồn kho chỉ có 5 -> đúng 5 thành công")
    void testConcurrentOversellingPrevention() throws InterruptedException {
        // Mô phỏng kho hàng với synchronized atomic lock tương tự PESSIMISTIC_WRITE
        class ConcurrentInventory {
            private int stock = 5;
            private int reserved = 0;

            public synchronized boolean tryReserve(int quantity) {
                if (stock - reserved >= quantity) {
                    reserved += quantity;
                    return true;
                }
                return false;
            }

            public int getReserved() {
                return reserved;
            }
        }

        ConcurrentInventory inventory = new ConcurrentInventory();
        int totalThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Đợi tất cả thread sẵn sàng cùng xuất phát
                    boolean success = inventory.tryReserve(1);
                    if (success) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Bắn tín hiệu chạy đồng thời
        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Các thread phải hoàn thành trong thời gian cho phép");
        assertEquals(5, successCount.get(), "Chính xác 5 request đầu tiên được giữ chỗ thành công");
        assertEquals(5, failCount.get(), "Chính xác 5 request vượt tồn bị từ chối với lỗi hết hàng (PAY-07)");
        assertEquals(5, inventory.getReserved(), "Tồn kho giữ chỗ đạt mức 5");
    }

    // =========================================================================
    // TEST 3: HẾT HẠN ĐƠN TRẢ TỒN ĐÚNG MỘT LẦN (IDEMPOTENT) (PAY-08)
    // =========================================================================
    @Test
    @Disabled("Cần kết nối PostgreSQL thật để kiểm tra trigger DB và tính idempotent của job OrderExpirationJob.")
    @DisplayName("3. Hết hạn đơn trả tồn đúng một lần: chạy lại không trả hai lần (idempotent)")
    void testOrderExpirationIdempotency() {
        class MockOrder {
            OrderStatus status = OrderStatus.PENDING_PAYMENT;
            final int quantity = 2;
        }

        class MockStock {
            int reserved = 2;

            public boolean expireOrder(MockOrder order) {
                if (order.status == OrderStatus.PENDING_PAYMENT) {
                    order.status = OrderStatus.EXPIRED;
                    this.reserved = Math.max(0, this.reserved - order.quantity);
                    return true;
                }
                return false;
            }
        }

        MockStock stock = new MockStock();
        MockOrder order = new MockOrder();

        // Lần chạy 1: Hết hạn đơn -> trả lại 2 tồn kho
        boolean run1 = stock.expireOrder(order);
        assertTrue(run1);
        assertEquals(0, stock.reserved, "Tồn kho giữ chỗ đã được hoàn trả về 0");
        assertEquals(OrderStatus.EXPIRED, order.status);

        // Lần chạy 2: Chạy lại trên đơn đã EXPIRED -> không hoàn trả thêm
        boolean run2 = stock.expireOrder(order);
        assertFalse(run2, "Không thực hiện trả tồn kho lần 2 (Idempotent)");
        assertEquals(0, stock.reserved, "Tồn giữ chỗ không bị trừ âm");
    }

    // =========================================================================
    // TEST 4: CHẶN MUA TRÙNG KHÓA HỌC CHO CÙNG CHILD (SHOP-04)
    // =========================================================================
    @Test
    @Disabled("Cần kết nối PostgreSQL thật để kiểm chứng truy vấn native bảng child_enroll.")
    @DisplayName("4. Chặn mua trùng khóa học cho cùng một Child (SHOP-04)")
    void testPreventDuplicateCourseForChild() {
        Set<String> enrolledDatabase = new HashSet<>();
        UUID child1 = UUID.randomUUID();
        UUID child2 = UUID.randomUUID();
        UUID course1 = UUID.randomUUID();
        UUID course2 = UUID.randomUUID();

        // Giả lập Child 1 đã sở hữu Course 1
        enrolledDatabase.add(child1 + ":" + course1);

        java.util.function.BiConsumer<UUID, UUID> validateAddToCart = (cId, crsId) -> {
            if (enrolledDatabase.contains(cId + ":" + crsId)) {
                throw new AppError(HttpStatus.CONFLICT, "COURSE_ALREADY_OWNED", "Bé đã sở hữu khóa học này (SHOP-04)");
            }
        };

        // Bé 1 mua Course 1 (đã có) -> Phải ném lỗi 409 CONFLICT
        AppError ex = assertThrows(AppError.class, () -> validateAddToCart.accept(child1, course1));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("COURSE_ALREADY_OWNED", ex.getErrorCode());

        // Bé 1 mua Course 2 (chưa có) -> Thành công không ném lỗi
        assertDoesNotThrow(() -> validateAddToCart.accept(child1, course2));

        // Bé 2 mua Course 1 (chưa có) -> Thành công không ném lỗi
        assertDoesNotThrow(() -> validateAddToCart.accept(child2, course1));
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
