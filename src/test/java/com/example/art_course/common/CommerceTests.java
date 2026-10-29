package com.example.art_course.common;

import com.example.art_course.common.statemachine.OrderStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PROMPT 3 — JUNIT 5 TEST SUITE (Task A: Commerce & Orders)")
public class CommerceTests {


    // Tách đơn được kiểm tra qua OrderService thật trong OrderServiceSafetyTest.

    // =========================================================================
    // TEST 2: BÁN VƯỢT TỒN KHI ĐỒNG THỜI TRÊN POSTGRESQL THẬT (PAY-07)
    // =========================================================================
    @Test
    @DisplayName("2. Bán vượt tồn khi đồng thời (PostgreSQL thật + PESSIMISTIC_WRITE): 10 threads mua đồng thời nhưng tồn kho chỉ có 5 -> đúng 5 thành công")
    void testConcurrentOversellingPrevention() throws InterruptedException {
        var ctx = PostgresTestcontainerEnv.getContext();
        var jdbc = ctx.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var orderService = ctx.getBean(com.example.art_course.modules.order.service.OrderService.class);
        var supplyRepo = ctx.getBean(com.example.art_course.supply.repository.SupplyRepository.class);

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
                    req.setRecipientName("Người nhận test");
                    req.setRecipientPhone("0901234567");
                    req.setShippingAddress("1 Đường Test, Phường Test, Quận Test, Tỉnh Test");
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
        var supplyRepo = ctx.getBean(com.example.art_course.supply.repository.SupplyRepository.class);

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
        UUID child3 = UUID.randomUUID();
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
        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'CHILD', 'ACTIVE')",
                child3, "child3_dup_" + child3);
        jdbc.update("INSERT INTO public.child_profiles (user_id, parent_id, display_name, birth_year) VALUES (?, ?, 'Be 3', 2016)",
                child3, parentId);
        jdbc.update("INSERT INTO public.courses (id, teacher_id, name, price, status) VALUES (?, ?, 'Khoa ve', 250000, 'PUBLISHED')",
                course1, teacherId);

        // Child 1 đã sở hữu Course 1: code cấp quyền ghi 'active' (chữ thường); child 3 là dữ liệu cũ viết hoa
        jdbc.update("INSERT INTO public.child_enroll (child_id, course_id, status) VALUES (?, ?, 'active')",
                child1, course1);
        jdbc.update("INSERT INTO public.child_enroll (child_id, course_id, status) VALUES (?, ?, 'ACTIVE')",
                child3, course1);

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

        // Quyền học viết hoa cũng bị chặn: so sánh trạng thái không phân biệt hoa thường
        var item3 = new com.example.art_course.modules.order.service.OrderService.CheckoutItem();
        item3.setItemType("COURSE");
        item3.setCourseId(course1);
        item3.setChildId(child3);
        item3.setQuantity(1);
        var req3 = new com.example.art_course.modules.order.service.OrderService.CheckoutRequest();
        req3.setUseCart(false);
        req3.setItems(List.of(item3));
        assertEquals("COURSE_ALREADY_OWNED",
                assertThrows(AppError.class, () -> orderService.createOrdersFromCheckout(parentId, req3)).getErrorCode());

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

    // JWT được kiểm tra qua FilterChain thật trong OrderSecurityWebMvcTest.

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
