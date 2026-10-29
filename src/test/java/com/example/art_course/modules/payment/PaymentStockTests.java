package com.example.art_course.modules.payment;

import com.example.art_course.common.statemachine.PaymentStatus;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.payment.repository.WebhookLogRepository;
import com.example.art_course.modules.payment.service.PaymentService;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Thanh toán dụng cụ: trừ tồn kho, hết hàng thì OUT_OF_STOCK, xử lý lặp không trừ hai lần")
class PaymentStockTests {

    private final OrderRepository orderRepo = mock(OrderRepository.class);
    private final OrderDetailRepository detailRepo = mock(OrderDetailRepository.class);
    private final PaymentRepository paymentRepo = mock(PaymentRepository.class);
    private final SupplyRepository supplyRepo = mock(SupplyRepository.class);
    private final PaymentService service = new PaymentService(
            orderRepo, detailRepo, paymentRepo,
            mock(WebhookLogRepository.class),
            mock(MoneyLedgerRepository.class),
            supplyRepo, null, null,
            mock(NotificationService.class), null, org.mockito.Mockito.mock(com.example.art_course.modules.refund.repository.RefundRepository.class), org.mockito.Mockito.mock(com.example.art_course.modules.refund.repository.RefundItemRepository.class), org.mockito.Mockito.mock(com.example.art_course.enrollment.repository.EnrollmentRepository.class), org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));

    private final UUID paymentId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID supplyId = UUID.randomUUID();
    private Payment payment;
    private Supply supply;

    @BeforeEach
    void setUp() {
        payment = Payment.builder().id(paymentId).orderId(orderId).status(PaymentStatus.PENDING)
                .amount(new BigDecimal("100000")).netAmount(new BigDecimal("90000")).teacherId(UUID.randomUUID()).build();
        Order order = Order.builder().id(orderId).parentId(UUID.randomUUID()).teacherId(UUID.randomUUID()).build();
        OrderDetail detail = OrderDetail.builder().orderId(orderId).supplyId(supplyId).quantity(2).build();
        supply = Supply.builder().id(supplyId).stockQuantity(2).reservedQuantity(2).soldQuantity(0).status("ACTIVE").build();

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(detailRepo.findByOrderId(orderId)).thenReturn(List.of(detail));
        when(supplyRepo.findByIdForUpdate(supplyId)).thenReturn(Optional.of(supply));
    }

    @Test
    @DisplayName("mua hết số còn lại: stock về 0, reserved về 0, sold tăng, trạng thái OUT_OF_STOCK")
    void lastUnitsSold() {
        when(paymentRepo.transition(paymentId, PaymentStatus.PENDING, PaymentStatus.HELD)).thenReturn(1);

        service.processSuccessfulPayment(payment, "tx-1", "PAYOS");

        assertEquals(0, supply.getStockQuantity());
        assertEquals(0, supply.getReservedQuantity());
        assertEquals(2, supply.getSoldQuantity());
        assertEquals("OUT_OF_STOCK", supply.getStatus());
    }

    @Test
    @DisplayName("còn dư hàng thì vẫn ACTIVE, chỉ trừ đúng số đã bán")
    void someStockLeft() {
        supply.setStockQuantity(5);
        when(paymentRepo.transition(paymentId, PaymentStatus.PENDING, PaymentStatus.HELD)).thenReturn(1);

        service.processSuccessfulPayment(payment, "tx-1", "PAYOS");

        assertEquals(3, supply.getStockQuantity());
        assertEquals("ACTIVE", supply.getStatus());
    }

    @Test
    @DisplayName("nơi khác đã xử lý giao dịch này rồi: không đụng tồn kho, đơn hay sổ cái")
    void secondProcessingDoesNothing() {
        when(paymentRepo.transition(paymentId, PaymentStatus.PENDING, PaymentStatus.HELD)).thenReturn(0);

        service.processSuccessfulPayment(payment, "tx-1", "PAYOS");

        assertEquals(2, supply.getStockQuantity());
        verify(supplyRepo, never()).save(any());
        verify(orderRepo, never()).save(any());
    }
}
