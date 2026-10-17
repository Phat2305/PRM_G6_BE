package com.example.art_course.modules.finance;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.DeliveryStatus;
import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.modules.course.entity.CourseReview;
import com.example.art_course.modules.course.repository.CourseReviewRepository;
import com.example.art_course.modules.finance.dto.TeacherBalanceDto;
import com.example.art_course.modules.finance.service.EscrowReleaseService;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.MoneyLedger;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.product.repository.SupplyRepository;
import com.example.art_course.modules.refund.dto.CreateRefundRequest;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.repository.RefundItemRepository;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.refund.service.RefundService;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.modules.withdrawal.dto.CreateWithdrawalRequest;
import com.example.art_course.modules.withdrawal.dto.RejectWithdrawalRequest;
import com.example.art_course.modules.withdrawal.entity.WithdrawalRequest;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PROMPT 5 — JUNIT 5 TEST SUITE (Escrow, Ledger, Withdrawals, Refunds)")
public class FinanceTests {

    // =========================================================================
    // TEST 1: GIẢI PHÓNG KHI CHỈ MỘT PHẦN DÒNG ĐỦ ĐIỀU KIỆN (MỤC 7.1, #26)
    // =========================================================================
    @Test
    @DisplayName("1. Giải phóng khi chỉ một phần dòng đủ điều kiện: từ chối giải phóng, giữ HELD (#26)")
    void testEscrow_PartialEligibleDoesNotRelease() {
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        OrderRepository orderRepo = mock(OrderRepository.class);
        OrderDetailRepository orderDetailRepo = mock(OrderDetailRepository.class);
        CourseReviewRepository courseReviewRepo = mock(CourseReviewRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        LedgerService ledgerService = mock(LedgerService.class);
        NotificationService notifService = mock(NotificationService.class);
        ConfigService configService = mock(ConfigService.class);

        EscrowReleaseService escrowService = new EscrowReleaseService(
                paymentRepo, orderRepo, orderDetailRepo, courseReviewRepo,
                refundRepo, ledgerService, notifService, configService
        );

        UUID orderId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        Order order = Order.builder().id(orderId).parentId(parentId).teacherId(teacherId).build();
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .teacherId(teacherId)
                .netAmount(new BigDecimal("450000"))
                .status(PaymentStatus.HELD)
                .build();

        // Đơn gồm 2 dòng:
        // Dòng 1: COURSE đã được Parent đánh giá (đủ điều kiện)
        OrderDetail courseDetail = OrderDetail.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .productType("COURSE")
                .courseId(courseId)
                .productName("Khóa học vẽ nét cơ bản")
                .build();

        // Dòng 2: SUPPLY vẫn đang SHIPPING, chưa COMPLETED (chưa đủ điều kiện)
        OrderDetail supplyDetail = OrderDetail.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .productType("SUPPLY")
                .deliveryStatus(DeliveryStatus.SHIPPING)
                .productName("Bộ cọ vẽ chuyên dụng")
                .build();

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(refundRepo.findByOrderId(orderId)).thenReturn(Collections.emptyList());
        when(orderDetailRepo.findByOrderId(orderId)).thenReturn(List.of(courseDetail, supplyDetail));

        // Khóa học đã được review bởi parent
        when(courseReviewRepo.findByCourseIdAndParentId(courseId, parentId))
                .thenReturn(Optional.of(new CourseReview()));

        // Chạy giải phóng
        boolean released = escrowService.tryReleaseEscrow(orderId);

        // Kiểm chứng: Không được giải phóng
        assertFalse(released, "Đơn hàng chỉ có 1 phần dòng hoàn tất thì KHÔNG được giải phóng (#26)");
        assertEquals(PaymentStatus.HELD, payment.getStatus(), "Payment vẫn phải giữ trạng thái HELD");
        verify(ledgerService, never()).recordEntry(any(), eq("RELEASE"), any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // TEST 2: RÚT VƯỢT SỐ DƯ KHẢ DỤNG (FIN-04)
    // =========================================================================
    @Test
    @DisplayName("2. Rút vượt số dư khả dụng: ném INSUFFICIENT_BALANCE và không trừ sổ cái")
    void testWithdrawal_ExceedsAvailableBalance() {
        WithdrawalRequestRepository withdrawalRepo = mock(WithdrawalRequestRepository.class);
        MoneyLedgerRepository ledgerRepo = mock(MoneyLedgerRepository.class);
        LedgerService ledgerService = mock(LedgerService.class);
        NotificationService notifService = mock(NotificationService.class);

        WithdrawalService withdrawalService = new WithdrawalService(
                withdrawalRepo, ledgerRepo, ledgerService, notifService
        );

        UUID teacherId = UUID.randomUUID();
        // Số dư khả dụng hiện tại là 300.000 VND
        when(ledgerRepo.calculateAvailableBalance(teacherId)).thenReturn(new BigDecimal("300000"));
        when(withdrawalRepo.existsByTeacherIdAndStatusIn(eq(teacherId), any())).thenReturn(false);

        // Yêu cầu rút 500.000 VND (> 300.000 VND)
        CreateWithdrawalRequest req = CreateWithdrawalRequest.builder()
                .amount(new BigDecimal("500000"))
                .bankInfo("MBBank - 123456789 - NGUYEN VAN A")
                .build();

        AppError error = assertThrows(AppError.class, () ->
                withdrawalService.requestWithdrawal(teacherId, req)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("INSUFFICIENT_BALANCE", error.getErrorCode());
        assertTrue(error.getMessage().contains("vượt quá số dư khả dụng"));

        verify(withdrawalRepo, never()).save(any());
        verify(ledgerService, never()).recordEntry(any(), eq("WITHDRAW_LOCK"), any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // TEST 3: HAI YÊU CẦU RÚT TIỀN ĐỒNG THỜI CỦA 1 TEACHER (BR-15)
    // =========================================================================
    @Test
    @DisplayName("3. Hai yêu cầu rút tiền đồng thời: chặn tạo mới khi đã có yêu cầu PENDING/APPROVED (BR-15)")
    void testWithdrawal_DuplicateActiveRequestBlocked() {
        WithdrawalRequestRepository withdrawalRepo = mock(WithdrawalRequestRepository.class);
        MoneyLedgerRepository ledgerRepo = mock(MoneyLedgerRepository.class);
        LedgerService ledgerService = mock(LedgerService.class);
        NotificationService notifService = mock(NotificationService.class);

        WithdrawalService withdrawalService = new WithdrawalService(
                withdrawalRepo, ledgerRepo, ledgerService, notifService
        );

        UUID teacherId = UUID.randomUUID();
        // Teacher đã có 1 yêu cầu PENDING đang xử lý
        when(withdrawalRepo.existsByTeacherIdAndStatusIn(eq(teacherId), any())).thenReturn(true);

        CreateWithdrawalRequest req = CreateWithdrawalRequest.builder()
                .amount(new BigDecimal("100000"))
                .bankInfo("MBBank - 123456789")
                .build();

        AppError error = assertThrows(AppError.class, () ->
                withdrawalService.requestWithdrawal(teacherId, req)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("ACTIVE_WITHDRAWAL_EXISTS", error.getErrorCode());
        assertTrue(error.getMessage().contains("BR-15"));

        verify(withdrawalRepo, never()).save(any());
    }

    // =========================================================================
    // TEST 4: TỪ CHỐI TRẢ SỐ DƯ CHÍNH XÁC (FIN-05)
    // =========================================================================
    @Test
    @DisplayName("4. Từ chối trả số dư chính xác: ghi sổ cái WITHDRAW_UNLOCK và chuyển sang REJECTED")
    void testWithdrawal_RejectRefundsBalanceAccurately() {
        WithdrawalRequestRepository withdrawalRepo = mock(WithdrawalRequestRepository.class);
        MoneyLedgerRepository ledgerRepo = mock(MoneyLedgerRepository.class);
        LedgerService ledgerService = mock(LedgerService.class);
        NotificationService notifService = mock(NotificationService.class);

        WithdrawalService withdrawalService = new WithdrawalService(
                withdrawalRepo, ledgerRepo, ledgerService, notifService
        );

        UUID requestId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("400000");

        WithdrawalRequest request = WithdrawalRequest.builder()
                .id(requestId)
                .teacherId(teacherId)
                .totalAmount(amount)
                .status(WithdrawalStatus.PENDING)
                .build();

        when(withdrawalRepo.findById(requestId)).thenReturn(Optional.of(request));
        when(withdrawalRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RejectWithdrawalRequest rejectReq = new RejectWithdrawalRequest("Thông tin số tài khoản không đúng tên đăng ký");
        withdrawalService.rejectWithdrawal(requestId, adminId, rejectReq);

        // Kiểm chứng trạng thái chuyển sang REJECTED
        assertEquals(WithdrawalStatus.REJECTED, request.getStatus());
        assertEquals("Thông tin số tài khoản không đúng tên đăng ký", request.getRejectedReason());

        // Kiểm chứng ghi bút toán WITHDRAW_UNLOCK trả lại số dư đúng 400.000 VND
        verify(ledgerService).recordEntry(
                eq(teacherId),
                eq("WITHDRAW_UNLOCK"),
                eq(amount),
                isNull(),
                isNull(),
                eq(requestId),
                contains("Hoàn trả số dư"),
                eq(adminId)
        );

        verify(notifService).notifyWithdrawalRejected(eq(requestId), eq(teacherId), eq(amount), any());
    }

    // =========================================================================
    // TEST 5: HOÀN VƯỢT SỐ TIỀN ĐÃ THANH TOÁN (FIN-07)
    // =========================================================================
    @Test
    @DisplayName("5. Hoàn vượt số tiền đã thanh toán: ném REFUND_EXCEEDS_PAYMENT và từ chối tạo hoàn tiền")
    void testRefund_ExceedsPaidAmount() {
        RefundRepository refundRepo = mock(RefundRepository.class);
        RefundItemRepository refundItemRepo = mock(RefundItemRepository.class);
        OrderRepository orderRepo = mock(OrderRepository.class);
        OrderDetailRepository orderDetailRepo = mock(OrderDetailRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        SupplyRepository supplyRepo = mock(SupplyRepository.class);
        IEnrollmentContract enrollmentContract = mock(IEnrollmentContract.class);
        LedgerService ledgerService = mock(LedgerService.class);
        NotificationService notifService = mock(NotificationService.class);

        RefundService refundService = new RefundService(
                refundRepo, refundItemRepo, orderRepo, orderDetailRepo,
                paymentRepo, supplyRepo, enrollmentContract, ledgerService, notifService
        );

        UUID orderId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID detailId = UUID.randomUUID();

        Order order = Order.builder().id(orderId).parentId(parentId).build();
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("500000")) // Đơn thanh toán 500.000 VND
                .status(PaymentStatus.HELD)
                .build();

        // Dòng yêu cầu hoàn trị giá 300.000 VND
        OrderDetail detail = OrderDetail.builder()
                .id(detailId)
                .orderId(orderId)
                .subTotal(new BigDecimal("300000"))
                .build();

        // Đã có 1 yêu cầu hoàn 300.000 VND trước đó (tổng sẽ thành 600.000 > 500.000)
        Refund existingRefund = Refund.builder()
                .totalAmount(new BigDecimal("300000"))
                .status("APPROVED")
                .build();

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(orderDetailRepo.findById(detailId)).thenReturn(Optional.of(detail));
        when(refundRepo.findByOrderId(orderId)).thenReturn(List.of(existingRefund));

        CreateRefundRequest req = CreateRefundRequest.builder()
                .orderId(orderId)
                .orderDetailId(detailId)
                .reason("Muốn đổi khóa học khác")
                .bankInfo("Techcombank - 987654321")
                .build();

        AppError error = assertThrows(AppError.class, () ->
                refundService.requestRefund(parentId, req)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("REFUND_EXCEEDS_PAYMENT", error.getErrorCode());
        assertTrue(error.getMessage().contains("vượt quá số tiền đã thanh toán"));

        verify(refundRepo, never()).save(any());
    }

    // =========================================================================
    // TEST 6: SỔ CÁI MONEY_LEDGER KHÔNG UPDATE/DELETE/TRUNCATE ĐƯỢC (TRIGGER BẢO MẬT)
    // =========================================================================
    @Test
    @DisplayName("6. Sổ cái money_ledger không UPDATE/DELETE/TRUNCATE được (Trigger kiểm chứng trên PostgreSQL thật)")
    void testMoneyLedger_PreventUpdateAndDeleteOnRealDb() {
        var ctx = com.example.art_course.common.PostgresTestcontainerEnv.getContext();
        var jdbc = ctx.getBean(org.springframework.jdbc.core.JdbcTemplate.class);

        UUID teacherId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();

        jdbc.update("INSERT INTO public.users (id, username, password, role, status) VALUES (?, ?, 'pw', 'TEACHER', 'ACTIVE')",
                teacherId, "teacher_ledger_" + teacherId);
        jdbc.update("INSERT INTO public.money_ledger (id, teacher_id, entry_type, amount, note) VALUES (?, ?, 'HOLD', 200000, 'Test append-only')",
                ledgerId, teacherId);

        // 1. UPDATE bị trigger chặn
        Exception updateEx = assertThrows(Exception.class, () ->
                jdbc.update("UPDATE public.money_ledger SET amount = 999999 WHERE id = ?", ledgerId)
        );
        assertTrue(updateEx.getMessage().contains("append-only"));

        // 2. DELETE bị trigger chặn
        Exception deleteEx = assertThrows(Exception.class, () ->
                jdbc.update("DELETE FROM public.money_ledger WHERE id = ?", ledgerId)
        );
        assertTrue(deleteEx.getMessage().contains("append-only"));

        // 3. TRUNCATE bị statement-level trigger V2 chặn
        Exception truncEx = assertThrows(Exception.class, () ->
                jdbc.execute("TRUNCATE TABLE public.money_ledger")
        );
        assertTrue(truncEx.getMessage().contains("append-only"));
    }

    // =========================================================================
    // TEST 7: TỔNG SỐ DƯ KHỚP TỔNG SỔ CÁI BẤT BIẾN (MATHEMATICAL LEDGER INTEGRITY)
    // =========================================================================
    @Test
    @DisplayName("7. Tổng số dư khớp tổng sổ cái bất biến: công thức số dư bảo toàn chính xác từng đồng")
    void testLedger_BalanceCalculationIntegrity() {
        MoneyLedgerRepository ledgerRepo = mock(MoneyLedgerRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        WithdrawalRequestRepository withdrawalRepo = mock(WithdrawalRequestRepository.class);

        LedgerService ledgerService = new LedgerService(ledgerRepo, paymentRepo, withdrawalRepo);

        UUID teacherId = UUID.randomUUID();

        // Giả lập lịch sử sổ cái money_ledger:
        // +1.000.000 VND (RELEASE đơn 1)
        // +500.000 VND (RELEASE đơn 2)
        // -400.000 VND (WITHDRAW_LOCK)
        // +400.000 VND (WITHDRAW_UNLOCK do từ chối)
        // -300.000 VND (WITHDRAW_LOCK yêu cầu rút 2)
        // -200.000 VND (REFUND hoàn tiền)
        // -> Expected Available Balance = 1.000.000 + 500.000 - 400.000 + 400.000 - 300.000 - 200.000 = 1.000.000 VND
        List<MoneyLedger> ledgerEntries = List.of(
                MoneyLedger.builder().entryType("RELEASE").amount(new BigDecimal("1000000")).build(),
                MoneyLedger.builder().entryType("RELEASE").amount(new BigDecimal("500000")).build(),
                MoneyLedger.builder().entryType("WITHDRAW_LOCK").amount(new BigDecimal("400000")).build(),
                MoneyLedger.builder().entryType("WITHDRAW_UNLOCK").amount(new BigDecimal("400000")).build(),
                MoneyLedger.builder().entryType("WITHDRAW_LOCK").amount(new BigDecimal("300000")).build(),
                MoneyLedger.builder().entryType("REFUND").amount(new BigDecimal("200000")).build()
        );

        BigDecimal calculatedFromLedgerEntries = BigDecimal.ZERO;
        for (MoneyLedger entry : ledgerEntries) {
            if ("RELEASE".equals(entry.getEntryType()) || "WITHDRAW_UNLOCK".equals(entry.getEntryType())) {
                calculatedFromLedgerEntries = calculatedFromLedgerEntries.add(entry.getAmount());
            } else if ("WITHDRAW_LOCK".equals(entry.getEntryType()) || "REFUND".equals(entry.getEntryType())) {
                calculatedFromLedgerEntries = calculatedFromLedgerEntries.subtract(entry.getAmount());
            }
        }

        assertEquals(0, new BigDecimal("1000000").compareTo(calculatedFromLedgerEntries));

        // Mock kết quả repository trả về đúng công thức này
        when(ledgerRepo.calculateAvailableBalance(teacherId)).thenReturn(calculatedFromLedgerEntries);
        when(ledgerRepo.findByTeacherIdOrderByCreatedAtDesc(teacherId)).thenReturn(ledgerEntries);

        // Payments: 1 đơn HELD trị giá 250.000 VND
        Payment heldPayment = Payment.builder()
                .status(PaymentStatus.HELD)
                .netAmount(new BigDecimal("250000"))
                .build();
        when(paymentRepo.findByTeacherIdOrderByCreatedAtDesc(teacherId)).thenReturn(List.of(heldPayment));

        // Withdrawals: 1 yêu cầu PENDING trị giá 300.000 VND
        WithdrawalRequest pendingReq = WithdrawalRequest.builder()
                .status(WithdrawalStatus.PENDING)
                .totalAmount(new BigDecimal("300000"))
                .build();
        when(withdrawalRepo.findByTeacherIdOrderByCreatedAtDesc(teacherId)).thenReturn(List.of(pendingReq));

        // Gọi LedgerService
        TeacherBalanceDto balance = ledgerService.getTeacherBalance(teacherId);

        // Kiểm chứng các thành phần số dư
        assertEquals(0, new BigDecimal("250000").compareTo(balance.getHeldBalance()), "Số dư đang giữ HELD phải là 250.000 VND");
        assertEquals(0, new BigDecimal("1000000").compareTo(balance.getAvailableBalance()), "Số dư có thể rút phải là 1.000.000 VND");
        assertEquals(0, new BigDecimal("300000").compareTo(balance.getPendingBalance()), "Số dư đang chờ rút phải là 300.000 VND");
        assertEquals(6, balance.getHistory().size(), "Lịch sử sổ cái phải có đủ 6 bút toán");
    }
}
