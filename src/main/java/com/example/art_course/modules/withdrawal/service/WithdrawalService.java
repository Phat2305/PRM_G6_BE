package com.example.art_course.modules.withdrawal.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.StateMachineValidator;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.withdrawal.dto.CreateWithdrawalRequest;
import com.example.art_course.modules.withdrawal.dto.MarkPaidRequest;
import com.example.art_course.modules.withdrawal.dto.RejectWithdrawalRequest;
import com.example.art_course.modules.withdrawal.dto.WithdrawalResponseDto;
import com.example.art_course.modules.withdrawal.entity.WithdrawalRequest;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final MoneyLedgerRepository moneyLedgerRepository;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;

    /**
     * Teacher gửi yêu cầu rút tiền (FIN-04, BR-15).
     * Kiểm tra số dư khả dụng và giới hạn tối đa 1 yêu cầu PENDING/APPROVED.
     * Khóa số dư bằng bút toán WITHDRAW_LOCK.
     */
    @Transactional
    public WithdrawalResponseDto requestWithdrawal(UUID teacherId, CreateWithdrawalRequest request) {
        BigDecimal amount = request.getAmount();
        // FIN-04: áp dụng mức tối thiểu cả khi gọi service ngoài controller.
        if (amount == null || amount.compareTo(BigDecimal.valueOf(10000)) < 0) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Số tiền rút tối thiểu là 10.000 VND");
        }

        // BR-15: Tối đa 1 yêu cầu PENDING hoặc APPROVED tại một thời điểm
        boolean hasActive = withdrawalRequestRepository.existsByTeacherIdAndStatusIn(
                teacherId, List.of(WithdrawalStatus.PENDING, WithdrawalStatus.APPROVED)
        );
        if (hasActive) {
            throw new AppError(HttpStatus.BAD_REQUEST, "ACTIVE_WITHDRAWAL_EXISTS",
                    "Bạn đang có một yêu cầu rút tiền đang chờ xử lý. Theo quy định chỉ được tạo tối đa 1 yêu cầu đang chờ (BR-15).");
        }

        // Kiểm tra số dư khả dụng từ sổ cái
        BigDecimal availableBalance = moneyLedgerRepository.calculateAvailableBalance(teacherId);
        if (availableBalance == null || amount.compareTo(availableBalance) > 0) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE",
                    String.format("Số tiền yêu cầu rút (%s VND) vượt quá số dư khả dụng (%s VND)",
                            amount, availableBalance != null ? availableBalance : BigDecimal.ZERO));
        }

        // 1. Tạo yêu cầu rút tiền
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .teacherId(teacherId)
                .totalAmount(amount)
                .bankInfo(request.getBankInfo())
                .status(WithdrawalStatus.PENDING)
                .build();
        WithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);

        // 2. Ghi sổ cái WITHDRAW_LOCK khóa số tiền khả dụng
        ledgerService.recordEntry(
                teacherId,
                "WITHDRAW_LOCK",
                amount,
                null,
                null,
                saved.getId(),
                "Khóa số dư cho yêu cầu rút tiền " + saved.getId(),
                teacherId
        );

        log.info("[WITHDRAWAL-REQUEST] Teacher {} tạo yêu cầu rút {} VND thành công (ID: {})",
                teacherId, amount, saved.getId());

        return mapToDto(saved);
    }

    /**
     * Admin duyệt yêu cầu rút tiền (ADM-03, FIN-05).
     * Chuyển trạng thái sang APPROVED, chờ Staff chuyển khoản.
     */
    @Transactional
    public WithdrawalResponseDto approveWithdrawal(UUID requestId, UUID adminId) {
        WithdrawalRequest request = findRequest(requestId);

        validateTransition(request.getStatus(), WithdrawalStatus.APPROVED);

        request.setStatus(WithdrawalStatus.APPROVED);
        request.setApprovedBy(adminId);
        WithdrawalRequest updated = withdrawalRequestRepository.save(request);

        notificationService.notifyWithdrawalApproved(updated.getId(), updated.getTeacherId(), updated.getTotalAmount());
        log.info("[WITHDRAWAL-APPROVE] Admin {} duyệt yêu cầu rút tiền {}", adminId, requestId);

        return mapToDto(updated);
    }

    /**
     * Admin từ chối yêu cầu rút tiền kèm lý do (FIN-05).
     * Ghi sổ cái WITHDRAW_UNLOCK trả lại số dư cho Teacher.
     */
    @Transactional
    public WithdrawalResponseDto rejectWithdrawal(UUID requestId, UUID adminId, RejectWithdrawalRequest req) {
        WithdrawalRequest request = findRequest(requestId);

        validateTransition(request.getStatus(), WithdrawalStatus.REJECTED);

        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new AppError(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Lý do từ chối không được để trống");
        }

        request.setStatus(WithdrawalStatus.REJECTED);
        request.setRejectedReason(req.getReason());
        request.setApprovedBy(adminId);
        WithdrawalRequest updated = withdrawalRequestRepository.save(request);

        // Trả lại số dư bằng bút toán WITHDRAW_UNLOCK
        ledgerService.recordEntry(
                request.getTeacherId(),
                "WITHDRAW_UNLOCK",
                request.getTotalAmount(),
                null,
                null,
                request.getId(),
                "Hoàn trả số dư do từ chối yêu cầu rút tiền: " + req.getReason(),
                adminId
        );

        notificationService.notifyWithdrawalRejected(updated.getId(), updated.getTeacherId(), updated.getTotalAmount(), req.getReason());
        log.info("[WITHDRAWAL-REJECT] Admin {} từ chối yêu cầu rút tiền {}: {}", adminId, requestId, req.getReason());

        return mapToDto(updated);
    }

    /**
     * Staff chuyển khoản thủ công và đánh dấu PAID kèm mã giao dịch + ảnh chứng từ (FIN-06).
     * Ghi sổ cái PAYOUT.
     */
    @Transactional
    public WithdrawalResponseDto markPaidWithdrawal(UUID requestId, UUID staffId, MarkPaidRequest req) {
        WithdrawalRequest request = findRequest(requestId);

        validateTransition(request.getStatus(), WithdrawalStatus.PAID);

        request.setStatus(WithdrawalStatus.PAID);
        request.setPaidBy(staffId);
        request.setTransactionCode(req.getTransactionCode());
        request.setProofImageUrl(req.getProofImageUrl());
        WithdrawalRequest updated = withdrawalRequestRepository.save(request);

        // Ghi sổ cái PAYOUT
        ledgerService.recordEntry(
                request.getTeacherId(),
                "PAYOUT",
                request.getTotalAmount(),
                null,
                null,
                request.getId(),
                "Chi trả thành công yêu cầu rút tiền qua mã giao dịch: " + req.getTransactionCode(),
                staffId
        );

        notificationService.notifyWithdrawalPaid(updated.getId(), updated.getTeacherId(), updated.getTotalAmount(), req.getTransactionCode());
        log.info("[WITHDRAWAL-PAID] Staff {} đánh dấu hoàn tất chi trả yêu cầu {} (Mã GD: {})",
                staffId, requestId, req.getTransactionCode());

        return mapToDto(updated);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalResponseDto> getTeacherWithdrawals(UUID teacherId) {
        return withdrawalRequestRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId)
                .stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<WithdrawalResponseDto> getAllWithdrawals(WithdrawalStatus status) {
        List<WithdrawalRequest> list = status != null
                ? withdrawalRequestRepository.findByStatusOrderByCreatedAtDesc(status)
                : withdrawalRequestRepository.findAll();
        return list.stream().map(this::mapToDto).toList();
    }

    private WithdrawalRequest findRequest(UUID requestId) {
        return withdrawalRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND", "Không tìm thấy yêu cầu rút tiền: " + requestId));
    }

    private void validateTransition(WithdrawalStatus current, WithdrawalStatus next) {
        if (!StateMachineValidator.isValidWithdrawalTransition(current, next)) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_STATE_TRANSITION",
                    String.format("Không thể chuyển trạng thái yêu cầu rút tiền từ %s sang %s (chống duyệt/chi hai lần)", current, next));
        }
    }

    private WithdrawalResponseDto mapToDto(WithdrawalRequest req) {
        return WithdrawalResponseDto.builder()
                .id(req.getId())
                .teacherId(req.getTeacherId())
                .totalAmount(req.getTotalAmount())
                .bankInfo(req.getBankInfo())
                .status(req.getStatus())
                .approvedBy(req.getApprovedBy())
                .paidBy(req.getPaidBy())
                .rejectedReason(req.getRejectedReason())
                .transactionCode(req.getTransactionCode())
                .proofImageUrl(req.getProofImageUrl())
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();
    }
}
