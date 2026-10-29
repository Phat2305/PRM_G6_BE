package com.example.art_course.common;

import com.example.art_course.common.statemachine.*;
import com.example.art_course.modules.finance.service.*;
import com.example.art_course.modules.order.entity.*;
import com.example.art_course.modules.order.repository.*;
import com.example.art_course.modules.order.service.OrderService;
import com.example.art_course.modules.payment.entity.*;
import com.example.art_course.modules.payment.repository.*;
import com.example.art_course.modules.payment.service.PaymentService;
import com.example.art_course.modules.refund.dto.*;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.refund.service.RefundService;
import com.example.art_course.modules.withdrawal.dto.*;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Real Spring transaction proxies and isolated PostgreSQL; no application profile or .env.
class CommerceTransactionsTest {
    private ApplicationContext context;
    private OrderRepository orders;
    private OrderDetailRepository details;
    private PaymentRepository payments;
    private MoneyLedgerRepository entries;
    private RefundService refunds;
    private EscrowReleaseService escrow;
    private LedgerService ledger;

    @BeforeEach
    void setUp() {
        context = PostgresTestcontainerEnv.getContext();
        orders = context.getBean(OrderRepository.class);
        details = context.getBean(OrderDetailRepository.class);
        payments = context.getBean(PaymentRepository.class);
        entries = context.getBean(MoneyLedgerRepository.class);
        refunds = context.getBean(RefundService.class);
        escrow = context.getBean(EscrowReleaseService.class);
        ledger = context.getBean(LedgerService.class);
    }

    record Purchase(Order order, Payment payment, List<OrderDetail> lines, Supply supply) {}

    private Purchase purchase(boolean paid, int lineCount) {
        UUID teacher = UUID.randomUUID();
        Supply supply = context.getBean(SupplyRepository.class).saveAndFlush(Supply.builder()
                .teacherId(teacher).name("Test supply").price(new BigDecimal("100000"))
                .stockQuantity(paid ? 0 : lineCount).reservedQuantity(paid ? 0 : lineCount)
                .soldQuantity(paid ? lineCount : 0).status("ACTIVE").build());
        Order order = orders.saveAndFlush(Order.builder().teacherId(teacher).parentId(UUID.randomUUID())
                .totalAmount(new BigDecimal(100000L * lineCount)).status(paid ? OrderStatus.PAID : OrderStatus.PENDING_PAYMENT).build());
        List<OrderDetail> lines = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            lines.add(details.saveAndFlush(OrderDetail.builder().orderId(order.getId()).supplyId(supply.getId())
                    .productName("Test line").unitPrice(new BigDecimal("100000")).quantity(1)
                    .subTotal(new BigDecimal("100000")).commissionAmount(new BigDecimal("10000"))
                    .netAmount(new BigDecimal("90000"))
                    .deliveryStatus(paid ? DeliveryStatus.COMPLETED : DeliveryStatus.PENDING).build()));
        }
        Payment payment = payments.saveAndFlush(Payment.builder().orderId(order.getId()).teacherId(teacher)
                .gateway("PAYOS").gatewayOrderCode(context.getBean(JdbcTemplate.class)
                        .queryForObject("SELECT nextval('public.payos_order_code_seq')", Long.class))
                .amount(order.getTotalAmount()).commissionAmount(new BigDecimal(10000L * lineCount))
                .netAmount(new BigDecimal(90000L * lineCount)).status(paid ? PaymentStatus.HELD : PaymentStatus.PENDING).build());
        return new Purchase(order, payment, lines, supply);
    }

    private UUID requestRefund(Purchase purchase, int index) {
        CreateRefundRequest request = new CreateRefundRequest();
        request.setOrderId(purchase.order().getId());
        request.setOrderDetailId(purchase.lines().get(index).getId());
        request.setBankInfo("test bank");
        request.setReason("test refund");
        return refunds.requestRefund(purchase.order().getParentId(), request).getId();
    }

    private void completeRefund(UUID id) {
        UUID staff = UUID.randomUUID();
        refunds.approveRefund(id, staff);
        refunds.completeRefund(id, staff, new CompleteRefundRequest("TX-" + id, null));
    }

    private static <T> List<T> concurrent(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> a = pool.submit(() -> { start.await(); return first.call(); });
            Future<T> b = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void simultaneousRefundsReserveEachLineOnlyOnce() throws Exception {
        Purchase purchase = purchase(true, 2);
        Callable<String> request = () -> {
            try { requestRefund(purchase, 0); return "CREATED"; }
            catch (AppError ex) { return ex.getErrorCode(); }
        };
        List<String> results = concurrent(request, request);
        assertEquals(1, Collections.frequency(results, "CREATED"));
        assertEquals(1, Collections.frequency(results, "DUPLICATE_REFUND"));
        assertEquals(1, context.getBean(RefundRepository.class).findByOrderId(purchase.order().getId()).size());
    }

    @Test
    void simultaneousReleaseCreditsOnlyOnce() throws Exception {
        Purchase purchase = purchase(true, 1);
        List<Boolean> results = concurrent(() -> escrow.tryReleaseEscrow(purchase.order().getId()),
                () -> escrow.tryReleaseEscrow(purchase.order().getId()));
        assertEquals(1, Collections.frequency(results, true));
        assertEquals(0, new BigDecimal("90000").compareTo(entries.calculateAvailableBalance(purchase.order().getTeacherId())));
        assertEquals(1, entries.findByTeacherIdOrderByCreatedAtDesc(purchase.order().getTeacherId()).size());
    }

    @Test
    void failedLedgerInsertRollsBackReleaseAndCanBeRetried() throws Exception {
        Purchase purchase = purchase(true, 1);
        LedgerService target = AopTestUtils.getTargetObject(ledger);
        doThrow(new IllegalStateException("ledger unavailable")).when(target)
                .recordEntry(any(), eq("RELEASE"), any(), any(), isNull(), isNull(), anyString(), isNull());
        try {
            assertThrows(IllegalStateException.class, () -> escrow.tryReleaseEscrow(purchase.order().getId()));
            assertEquals(PaymentStatus.HELD, payments.findById(purchase.payment().getId()).orElseThrow().getStatus());
            assertTrue(entries.findByTeacherIdOrderByCreatedAtDesc(purchase.order().getTeacherId()).isEmpty());
        } finally {
            reset(target);
        }
        assertTrue(escrow.tryReleaseEscrow(purchase.order().getId()));
    }

    @Test
    void refundFromHeldThenReleasedPreservesOtherOrdersBalance() {
        Purchase purchase = purchase(true, 2);
        UUID teacher = purchase.order().getTeacherId();
        ledger.recordEntry(teacher, "RELEASE", new BigDecimal("50000"), null, null, null, "Other order", null);
        completeRefund(requestRefund(purchase, 0));
        assertEquals(0, new BigDecimal("90000").compareTo(ledger.getTeacherBalance(teacher).getHeldBalance()));
        assertEquals(0, new BigDecimal("50000").compareTo(entries.calculateAvailableBalance(teacher)));
        assertTrue(escrow.tryReleaseEscrow(purchase.order().getId()));
        assertEquals(0, new BigDecimal("140000").compareTo(entries.calculateAvailableBalance(teacher)));
        completeRefund(requestRefund(purchase, 1));
        assertEquals(PaymentStatus.REFUNDED, payments.findById(purchase.payment().getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("50000").compareTo(entries.calculateAvailableBalance(teacher)));
        assertEquals(0, ledger.getTeacherBalance(teacher).getHeldBalance().signum());
    }

    @Test
    void simultaneousWithdrawalRejectionUnlocksOnlyOnce() throws Exception {
        UUID teacher = UUID.randomUUID();
        ledger.recordEntry(teacher, "RELEASE", new BigDecimal("100000"), null, null, null, "Available", null);
        WithdrawalService withdrawals = context.getBean(WithdrawalService.class);
        UUID id = withdrawals.requestWithdrawal(teacher, new CreateWithdrawalRequest(new BigDecimal("40000"), "test bank")).getId();
        Callable<String> reject = () -> {
            try { withdrawals.rejectWithdrawal(id, UUID.randomUUID(), new RejectWithdrawalRequest("test")); return "REJECTED"; }
            catch (AppError ex) { return ex.getErrorCode(); }
        };
        List<String> results = concurrent(reject, reject);
        assertEquals(1, Collections.frequency(results, "REJECTED"));
        assertEquals(1, Collections.frequency(results, "INVALID_STATE_TRANSITION"));
        assertEquals(0, new BigDecimal("100000").compareTo(entries.calculateAvailableBalance(teacher)));
    }

    @Test
    void cancelledOrderReceivingRealSignedWebhookDoesNotSellAgain() {
        Purchase purchase = purchase(false, 1);
        context.getBean(OrderService.class).cancelOrder(purchase.order().getId(), purchase.order().getParentId(), "PARENT");
        Map<String, String> webhook = PostgresTestcontainerEnv.payosWebhook(purchase.payment().getGatewayOrderCode(), "100000", "late-" + purchase.payment().getId());
        context.getBean(PaymentService.class).handleWebhook("PAYOS", webhook);
        context.getBean(PaymentService.class).handleWebhook("PAYOS", webhook);
        assertEquals(OrderStatus.CANCELLED, orders.findById(purchase.order().getId()).orElseThrow().getStatus());
        Supply stock = context.getBean(SupplyRepository.class).findById(purchase.supply().getId()).orElseThrow();
        assertEquals(1, stock.getStockQuantity());
        assertEquals(0, stock.getReservedQuantity());
        assertEquals(0, stock.getSoldQuantity());
        assertEquals(1, context.getBean(RefundRepository.class).findByOrderId(purchase.order().getId()).size());
        completeRefund(context.getBean(RefundRepository.class).findByOrderId(purchase.order().getId()).getFirst().getId());
        assertEquals(1, context.getBean(SupplyRepository.class).findById(stock.getId()).orElseThrow().getStockQuantity());
        assertEquals(PaymentStatus.REFUNDED, payments.findById(purchase.payment().getId()).orElseThrow().getStatus());
    }

    @Test
    void payosSequenceAllocatesUniqueCodesAcrossConnections() throws Exception {
        Callable<List<Long>> allocate = () -> {
            List<Long> codes = new ArrayList<>();
            for (int i = 0; i < 100; i++) codes.add(context.getBean(JdbcTemplate.class)
                    .queryForObject("SELECT nextval('public.payos_order_code_seq')", Long.class));
            return codes;
        };
        Set<Long> codes = new HashSet<>();
        for (List<Long> batch : concurrent(allocate, allocate)) codes.addAll(batch);
        assertEquals(200, codes.size());
    }
}
