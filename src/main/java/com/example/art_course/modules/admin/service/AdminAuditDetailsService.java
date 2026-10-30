package com.example.art_course.modules.admin.service;

import com.example.art_course.common.TeacherNames;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.modules.admin.dto.AuditDetailsDto;
import com.example.art_course.modules.admin.dto.StaffActivityDto;
import com.example.art_course.modules.moderation.entity.ModerationReview;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.payment.entity.MoneyLedger;
import com.example.art_course.modules.payment.entity.Payment;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.refund.entity.Refund;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.withdrawal.entity.WithdrawalRequest;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// ADM-04/05: chỉ bổ sung dữ liệu đọc; tra theo nhóm để mỗi dòng nhật ký không phát sinh thêm truy vấn.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditDetailsService {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final WithdrawalRequestRepository withdrawals;
    private final OrderRepository orders;
    private final OrderDetailRepository orderItems;
    private final CourseRepository courses;
    private final SupplyRepository supplies;
    private final CustomizeRequestRepository requests;
    private final UserRepository users;
    private final TeacherNames names;
    private final MoneyLedgerRepository ledger;

    public List<StaffActivityDto> staffActivities(UUID staffId, List<ModerationReview> reviews) {
        // shortcut: giữ API nhật ký chưa phân trang; bổ sung phân trang khi lịch sử từng Staff lớn.
        List<StaffActivityDto> result = new ArrayList<>(staffDetails(reviews));
        List<MoneyLedger> financial = ledger.findByCreatedByAndEntryTypeInOrderByCreatedAtDesc(
                staffId, List.of("PAYOUT", "REFUND", "WITHDRAW_UNLOCK"));
        var details = ledgerDetails(financial);
        for (MoneyLedger entry : financial) {
            boolean withdrawal = entry.getWithdrawalId() != null;
            String decision = switch (entry.getEntryType()) {
                case "PAYOUT" -> "PAID";
                case "REFUND" -> "COMPLETED";
                default -> "REJECTED";
            };
            result.add(new StaffActivityDto(entry.getId(), entry.getCreatedBy(), withdrawal ? "WITHDRAWAL" : "REFUND",
                    withdrawal ? entry.getWithdrawalId() : entry.getRefundId(), decision, entry.getNote(),
                    entry.getCreatedAt(), details.get(entry.getId())));
        }
        result.sort(Comparator.comparing(StaffActivityDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public Map<UUID, AuditDetailsDto> ledgerDetails(List<MoneyLedger> entries) {
        var paymentMap = load(ids(entries, MoneyLedger::getPaymentId), payments, Payment::getId);
        var refundMap = load(ids(entries, MoneyLedger::getRefundId), refunds, Refund::getId);
        var withdrawalMap = load(ids(entries, MoneyLedger::getWithdrawalId), withdrawals, WithdrawalRequest::getId);
        Set<UUID> orderIds = ids(paymentMap.values(), Payment::getOrderId);
        orderIds.addAll(ids(refundMap.values(), Refund::getOrderId));
        var orderMap = orderDetails(orderIds);
        var teachers = names.byIds(ids(withdrawalMap.values(), WithdrawalRequest::getTeacherId));
        Map<UUID, AuditDetailsDto> result = new HashMap<>();
        for (MoneyLedger entry : entries) {
            AuditDetailsDto detail = null;
            if (entry.getWithdrawalId() != null) {
                WithdrawalRequest withdrawal = withdrawalMap.get(entry.getWithdrawalId());
                if (withdrawal != null) {
                    var proof = proof(withdrawal.getId(), "WITHDRAWAL", withdrawal.getProofImageUrl());
                    detail = new AuditDetailsDto("Yêu cầu rút tiền", withdrawal.getRejectedReason(),
                            teachers.get(withdrawal.getTeacherId()), null, withdrawal.getTotalAmount(),
                            withdrawal.getStatus().name(), null, null, withdrawal.getBankInfo(), List.of(), proof);
                }
            } else if (entry.getRefundId() != null) {
                Refund refund = refundMap.get(entry.getRefundId());
                if (refund != null) detail = refundDetails(refund, orderMap.get(refund.getOrderId()));
            } else if (entry.getPaymentId() != null) {
                Payment payment = paymentMap.get(entry.getPaymentId());
                if (payment != null) detail = orderMap.get(payment.getOrderId());
            }
            if (detail != null) result.put(entry.getId(), detail);
        }
        return result;
    }

    public List<StaffActivityDto> staffDetails(List<ModerationReview> reviews) {
        var courseMap = load(targetIds(reviews, "COURSE"), courses, Course::getId);
        var supplyMap = load(targetIds(reviews, "SUPPLY"), supplies, Supply::getId);
        var requestMap = load(targetIds(reviews, "CUSTOMIZE_REQUEST"), requests, CustomizeRequest::getId);
        var refundMap = load(targetIds(reviews, "REFUND"), refunds, Refund::getId);
        var teacherMap = load(targetIds(reviews, "TEACHER_PROFILE"), users, User::getId);
        Set<UUID> teacherIds = ids(courseMap.values(), Course::getTeacherId);
        teacherIds.addAll(ids(supplyMap.values(), Supply::getTeacherId));
        teacherIds.addAll(teacherMap.keySet());
        var teacherNames = names.byIds(teacherIds);
        var parentNames = names.parentsByIds(ids(requestMap.values(), CustomizeRequest::getParentId));
        var orderMap = orderDetails(ids(refundMap.values(), Refund::getOrderId));
        List<StaffActivityDto> result = new ArrayList<>();
        for (ModerationReview review : reviews) {
            UUID id = review.getTargetId();
            AuditDetailsDto detail = switch (review.getTargetType().trim().toUpperCase(Locale.ROOT)) {
                case "COURSE" -> {
                    Course course = courseMap.get(id);
                    yield course == null ? null : new AuditDetailsDto(course.getName(), course.getDescription(),
                            teacherNames.get(course.getTeacherId()), null, course.getPrice(), course.getStatus(),
                            null, null, null, List.of(), List.of());
                }
                case "SUPPLY" -> {
                    Supply supply = supplyMap.get(id);
                    yield supply == null ? null : new AuditDetailsDto(supply.getName(), supply.getDescription(),
                            teacherNames.get(supply.getTeacherId()), null, supply.getPrice(), supply.getStatus(),
                            null, null, null, List.of(), List.of());
                }
                case "CUSTOMIZE_REQUEST" -> {
                    CustomizeRequest request = requestMap.get(id);
                    yield request == null ? null : new AuditDetailsDto(request.getTitle(), request.getDescription(),
                            null, parentNames.get(request.getParentId()), request.getBudget(), request.getStatus(),
                            null, null, null, List.of(), List.of());
                }
                case "TEACHER_PROFILE" -> {
                    User teacher = teacherMap.get(id);
                    String name = teacherNames.get(id);
                    yield teacher == null ? null : new AuditDetailsDto(name == null || name.isBlank() ? teacher.getUsername() : name,
                            null, teacherNames.get(id), null, null, teacher.getStatus(), null, null, null, List.of(), List.of());
                }
                case "REFUND" -> {
                    Refund refund = refundMap.get(id);
                    yield refund == null ? null : refundDetails(refund, orderMap.get(refund.getOrderId()));
                }
                default -> null;
            };
            result.add(new StaffActivityDto(review.getId(), review.getStaffId(), review.getTargetType(), review.getTargetId(),
                    review.getDecision(), review.getReason(), review.getCreatedAt(), detail));
        }
        return result;
    }

    private Map<UUID, AuditDetailsDto> orderDetails(Set<UUID> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        var orderMap = load(orderIds, orders, Order::getId);
        var teachers = names.byIds(ids(orderMap.values(), Order::getTeacherId));
        var parents = names.parentsByIds(ids(orderMap.values(), Order::getParentId));
        Map<UUID, List<AuditDetailsDto.Item>> items = new HashMap<>();
        for (var item : orderItems.findByOrderIdIn(orderIds)) {
            items.computeIfAbsent(item.getOrderId(), ignored -> new ArrayList<>()).add(
                    new AuditDetailsDto.Item(item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getSubTotal()));
        }
        Map<UUID, List<AuditDetailsDto.Proof>> proofs = new HashMap<>();
        for (Refund refund : refunds.findByOrderIdIn(orderIds)) {
            proofs.computeIfAbsent(refund.getOrderId(), ignored -> new ArrayList<>())
                    .addAll(proof(refund.getId(), "REFUND", refund.getProofImageUrl()));
        }
        Map<UUID, AuditDetailsDto> result = new HashMap<>();
        orderMap.forEach((id, order) -> result.put(id, new AuditDetailsDto("Đơn hàng", null,
                teachers.get(order.getTeacherId()), parents.get(order.getParentId()), order.getTotalAmount(),
                order.getStatus().name(), id, order.getStatus().name(), null,
                items.getOrDefault(id, List.of()), proofs.getOrDefault(id, List.of()))));
        return result;
    }

    private AuditDetailsDto refundDetails(Refund refund, AuditDetailsDto order) {
        List<AuditDetailsDto.Proof> proofs = order == null ? proof(refund.getId(), "REFUND", refund.getProofImageUrl()) : order.proofs();
        return new AuditDetailsDto("Yêu cầu hoàn tiền", refund.getReason(), order == null ? null : order.teacherName(),
                order == null ? null : order.parentName(), refund.getTotalAmount(), refund.getStatus(), refund.getOrderId(),
                order == null ? null : order.orderStatus(), refund.getBankInfo(), order == null ? List.of() : order.items(), proofs);
    }

    private List<AuditDetailsDto.Proof> proof(UUID id, String type, String url) {
        String expected = "WITHDRAWAL".equals(type) ? "/api/v1/teacher/finance/withdrawals/" + id + "/proof"
                : "/api/v1/refunds/" + id + "/proof";
        return expected.equals(url) ? List.of(new AuditDetailsDto.Proof(id, type, url)) : List.of();
    }

    private Set<UUID> targetIds(List<ModerationReview> reviews, String type) {
        return ids(reviews.stream().filter(r -> type.equalsIgnoreCase(r.getTargetType().trim())).toList(), ModerationReview::getTargetId);
    }

    private <T> Set<UUID> ids(Collection<T> rows, Function<T, UUID> key) {
        return rows.stream().map(key).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private <T> Map<UUID, T> load(Set<UUID> ids, JpaRepository<T, UUID> repository, Function<T, UUID> key) {
        if (ids.isEmpty()) return Map.of();
        return repository.findAllById(ids).stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
