package com.example.art_course.customize_request;

import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.gateway.PaymentGatewayFactory;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.payment.repository.WebhookLogRepository;
import com.example.art_course.modules.payment.service.EnrollmentGrantService;
import com.example.art_course.modules.payment.service.PaymentService;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CUS-09: thanh toán đơn CUSTOMIZE thành công thì yêu cầu chuyển FUNDED (giáo viên bắt đầu làm).
// E2E qua HTTP không chạy được bước này vì cần khóa PayOS thật, nên kiểm ở mức service.
class CustomizePaymentFundedTest {

    @Test
    void successfulPayment_movesRequestToFunded() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderDetailRepository details = mock(OrderDetailRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
        PaymentService service = new PaymentService(orders, details, payments, mock(WebhookLogRepository.class),
                mock(MoneyLedgerRepository.class), mock(SupplyRepository.class), mock(PaymentGatewayFactory.class),
                mock(EnrollmentGrantService.class), mock(NotificationService.class), mock(ConfigService.class), org.mockito.Mockito.mock(com.example.art_course.modules.refund.repository.RefundRepository.class), org.mockito.Mockito.mock(com.example.art_course.modules.refund.repository.RefundItemRepository.class), org.mockito.Mockito.mock(com.example.art_course.enrollment.repository.EnrollmentRepository.class), org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        ReflectionTestUtils.setField(service, "customizeRequestRepository", requests);

        UUID orderId = UUID.randomUUID();
        CustomizeRequest request = CustomizeRequest.builder()
                .id(UUID.randomUUID()).parentId(UUID.randomUUID()).status("ACCEPTED").build();
        when(orders.findByIdForUpdate(orderId)).thenReturn(Optional.of(Order.builder().id(orderId).build()));
        when(details.findByOrderId(orderId)).thenReturn(List.of(OrderDetail.builder()
                .orderId(orderId).customizeRequestId(request.getId()).productName("Vẽ mèo")
                .unitPrice(new BigDecimal("450000")).quantity(1).subTotal(new BigDecimal("450000")).build()));
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(payments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        // Nơi xử lý giành được dòng PENDING → HELD (chống webhook và job đối soát xử lý trùng).
        when(payments.transition(any(), any(), any())).thenReturn(1);

        Payment payment = Payment.builder().id(UUID.randomUUID()).orderId(orderId)
                .teacherId(UUID.randomUUID()).netAmount(new BigDecimal("360000")).build();
        service.processSuccessfulPayment(payment, "tx-1", "PAYOS");

        assertEquals("FUNDED", request.getStatus());
        verify(requests).save(request);
    }
}
