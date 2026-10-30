package com.example.art_course.modules.admin;

import com.example.art_course.common.TeacherNames;
import com.example.art_course.common.statemachine.OrderStatus;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.modules.admin.service.AdminAuditDetailsService;
import com.example.art_course.modules.moderation.entity.ModerationReview;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.entity.OrderDetail;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.payment.entity.MoneyLedger;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.withdrawal.entity.WithdrawalRequest;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// ADM-04/05: đọc thông tin liên quan theo nhóm, không ghi lại hoặc thay đổi lịch sử.
class AdminAuditDetailsTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final WithdrawalRequestRepository withdrawals = mock(WithdrawalRequestRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderDetailRepository items = mock(OrderDetailRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final SupplyRepository supplies = mock(SupplyRepository.class);
    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TeacherNames names = mock(TeacherNames.class);
    private final MoneyLedgerRepository ledger = mock(MoneyLedgerRepository.class);
    private final AdminAuditDetailsService service = new AdminAuditDetailsService(payments, refunds, withdrawals,
            orders, items, courses, supplies, requests, users, names, ledger);
    private final UUID teacher = UUID.randomUUID();
    private final UUID parent = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private MoneyLedger entry(String type, UUID paymentId, UUID refundId, UUID withdrawalId) {
        return MoneyLedger.builder().id(UUID.randomUUID()).teacherId(teacher).entryType(type)
                .amount(new BigDecimal("90000")).paymentId(paymentId).refundId(refundId).withdrawalId(withdrawalId)
                .note("Ghi chú gốc " + orderId).build();
    }

    private Refund orderFixture() {
        Refund refund = Refund.builder().id(UUID.randomUUID()).orderId(orderId).parentId(parent)
                .totalAmount(new BigDecimal("100000")).status("COMPLETED").reason("Giao thiếu").bankInfo("VCB - 00123").build();
        refund.setProofImageUrl("/api/v1/refunds/" + refund.getId() + "/proof");
        when(orders.findAllById(any())).thenReturn(List.of(Order.builder().id(orderId).teacherId(teacher).parentId(parent)
                .status(OrderStatus.PAID).totalAmount(new BigDecimal("200000")).build()));
        when(items.findByOrderIdIn(any())).thenReturn(List.of(OrderDetail.builder().id(UUID.randomUUID()).orderId(orderId)
                .productName("Giấy vẽ").quantity(2).unitPrice(new BigDecimal("100000")).subTotal(new BigDecimal("200000")).build()));
        when(refunds.findByOrderIdIn(any())).thenReturn(List.of(refund));
        when(names.byIds(any())).thenReturn(Map.of(teacher, "Cô Lan"));
        when(names.parentsByIds(any())).thenReturn(Map.of(parent, "Chị Mai"));
        return refund;
    }

    @Test
    void ledgerLinksPaymentAndRefundToOrderItemsAndAllOrderProofsWithoutChangingLedger() {
        Refund refund = orderFixture();
        Payment payment = Payment.builder().id(UUID.randomUUID()).orderId(orderId).build();
        when(payments.findAllById(any())).thenReturn(List.of(payment));
        when(refunds.findAllById(any())).thenReturn(List.of(refund));
        MoneyLedger release = entry("RELEASE", payment.getId(), null, null);
        MoneyLedger paidRefund = entry("REFUND", payment.getId(), refund.getId(), null);

        var details = service.ledgerDetails(List.of(release, paidRefund));

        assertEquals(orderId, details.get(release.getId()).orderId());
        assertEquals("Chị Mai", details.get(release.getId()).parentName());
        assertEquals("Cô Lan", details.get(release.getId()).teacherName());
        assertEquals("Giấy vẽ", details.get(release.getId()).items().getFirst().name());
        assertEquals(2, details.get(release.getId()).items().getFirst().quantity());
        assertEquals(refund.getProofImageUrl(), details.get(release.getId()).proofs().getFirst().url());
        assertEquals("VCB - 00123", details.get(paidRefund.getId()).bankInfo());
        assertEquals(refund.getTotalAmount(), details.get(paidRefund.getId()).totalAmount());
        assertEquals(new BigDecimal("90000"), paidRefund.getAmount());
        assertEquals("Ghi chú gốc " + orderId, paidRefund.getNote());
        verify(items).findByOrderIdIn(Set.of(orderId));
        verify(refunds).findByOrderIdIn(Set.of(orderId));
        verify(ledger, never()).save(any());
    }

    @Test
    void withdrawalShowsPrivateProofOnlyWhenItBelongsToThatRequest() {
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().id(UUID.randomUUID()).teacherId(teacher)
                .status(WithdrawalStatus.PAID).totalAmount(new BigDecimal("10000")).bankInfo("MB - 00123").build();
        withdrawal.setProofImageUrl("/api/v1/teacher/finance/withdrawals/" + withdrawal.getId() + "/proof");
        when(withdrawals.findAllById(any())).thenReturn(List.of(withdrawal));
        when(names.byIds(any())).thenReturn(Map.of(teacher, "Cô Lan"));
        MoneyLedger payout = entry("PAYOUT", null, null, withdrawal.getId());
        assertEquals(withdrawal.getProofImageUrl(), service.ledgerDetails(List.of(payout)).get(payout.getId()).proofs().getFirst().url());
        for (String url : List.of("https://external.test/proof.png", "/api/v1/teacher/finance/withdrawals/other/proof")) {
            withdrawal.setProofImageUrl(url);
            assertTrue(service.ledgerDetails(List.of(payout)).get(payout.getId()).proofs().isEmpty());
        }
    }

    private ModerationReview review(String type, UUID id) {
        return ModerationReview.builder().id(UUID.randomUUID()).targetType(type).targetId(id).decision("REJECTED")
                .reason("Lý do đã ghi").createdAt(Instant.parse("2026-10-01T00:00:00Z")).build();
    }

    @Test
    void staffReviewsExposeTargetNamesOwnersPricesAndOrderDetails() {
        Refund refund = orderFixture();
        Course course = Course.builder().id(UUID.randomUUID()).teacherId(teacher).name("Vẽ mèo").status("PUBLISHED").build();
        Supply supply = Supply.builder().id(UUID.randomUUID()).teacherId(teacher).name("Bộ chì").price(new BigDecimal("30000")).build();
        CustomizeRequest request = CustomizeRequest.builder().id(UUID.randomUUID()).parentId(parent).title("Khóa riêng").build();
        User user = new User(); user.setId(teacher); user.setUsername("co.lan");
        when(courses.findAllById(any())).thenReturn(List.of(course));
        when(supplies.findAllById(any())).thenReturn(List.of(supply));
        when(requests.findAllById(any())).thenReturn(List.of(request));
        when(users.findAllById(any())).thenReturn(List.of(user));
        when(refunds.findAllById(any())).thenReturn(List.of(refund));

        var result = service.staffDetails(List.of(review("COURSE", course.getId()), review("SUPPLY", supply.getId()),
                review("CUSTOMIZE_REQUEST", request.getId()), review("TEACHER_PROFILE", teacher), review("REFUND", refund.getId())));

        assertEquals("Vẽ mèo", result.get(0).details().title());
        assertEquals("Cô Lan", result.get(0).details().teacherName());
        assertEquals("Bộ chì", result.get(1).details().title());
        assertEquals("Chị Mai", result.get(2).details().parentName());
        assertEquals("Cô Lan", result.get(3).details().title());
        assertEquals(orderId, result.get(4).details().orderId());
        assertEquals("REJECTED", result.get(0).decision());
        assertEquals("Lý do đã ghi", result.get(0).reason());
    }

    @Test
    void missingTargetsKeepHistoricalDecisionAndMissingLedgerLinksStayReadable() {
        var original = review("SUPPLY", UUID.randomUUID());
        original.setStaffId(UUID.randomUUID());
        var result = service.staffDetails(List.of(original)).getFirst();
        assertEquals(original.getReason(), result.reason());
        assertEquals(original.getStaffId(), result.staffId());
        assertEquals(original.getTargetId(), result.targetId());
        assertNull(result.details());
        assertTrue(service.ledgerDetails(List.of(entry("HOLD", UUID.randomUUID(), null, null))).isEmpty());
    }

    @Test
    void teacherProfileUsesUsernameWhenFullNameIsMissing() {
        User user = new User(); user.setId(teacher); user.setUsername("co.lan");
        when(users.findAllById(any())).thenReturn(List.of(user));
        for (String name : Arrays.asList(null, "", "   ")) {
            when(names.byIds(any())).thenReturn(Collections.singletonMap(teacher, name));
            assertEquals("co.lan", service.staffDetails(List.of(review("TEACHER_PROFILE", teacher))).getFirst().details().title());
        }
    }

    @Test
    void staffHistoryIncludesOnlyRecordedFinancialActionsInTimestampOrder() {
        UUID staff = UUID.randomUUID();
        MoneyLedger payout = entry("PAYOUT", null, null, UUID.randomUUID());
        payout.setCreatedAt(Instant.parse("2026-10-02T00:00:00Z"));
        when(ledger.findByCreatedByAndEntryTypeInOrderByCreatedAtDesc(eq(staff), any())).thenReturn(List.of(payout));
        var result = service.staffActivities(staff, List.of(review("SUPPLY", UUID.randomUUID())));
        assertEquals(2, result.size());
        assertEquals("WITHDRAWAL", result.getFirst().targetType());
        assertEquals("PAID", result.getFirst().decision());
        assertEquals(payout.getCreatedAt(), result.getFirst().createdAt());
        assertEquals("REJECTED", result.get(1).decision());
        verify(ledger).findByCreatedByAndEntryTypeInOrderByCreatedAtDesc(staff, List.of("PAYOUT", "REFUND", "WITHDRAW_UNLOCK"));
    }
}
