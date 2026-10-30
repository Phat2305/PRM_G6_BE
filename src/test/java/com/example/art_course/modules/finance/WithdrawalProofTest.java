package com.example.art_course.modules.finance;

import com.example.art_course.common.AppError;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.withdrawal.entity.WithdrawalRequest;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import com.example.art_course.video.service.R2StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// FIN-06: không ghi chi nếu thiếu bằng chứng, tải lỗi hoặc đã chi; bằng chứng không công khai.
class WithdrawalProofTest {
    private final WithdrawalRequestRepository requests = mock(WithdrawalRequestRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final R2StorageService storage = mock(R2StorageService.class);
    private final WithdrawalService service = new WithdrawalService(requests, mock(MoneyLedgerRepository.class),
            ledger, notifications, storage);
    private final UUID staffId = UUID.randomUUID();
    private final WithdrawalRequest request = WithdrawalRequest.builder().id(UUID.randomUUID())
            .teacherId(UUID.randomUUID()).totalAmount(new BigDecimal("10000"))
            .status(WithdrawalStatus.APPROVED).build();

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "proof.png", "image/png", new byte[]{1, 2, 3});
    }

    @BeforeEach
    void setup() {
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(requests.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void successfulUploadMarksPaidAndRecordsOnePayoutWithoutFakeTransactionCode() {
        var result = service.markPaidWithProof(request.getId(), staffId, png());
        assertEquals(WithdrawalStatus.PAID, result.getStatus());
        assertEquals(staffId, result.getPaidBy());
        assertNull(result.getTransactionCode());
        assertEquals("/api/v1/teacher/finance/withdrawals/" + request.getId() + "/proof", result.getProofImageUrl());
        verify(storage).uploadBytes("withdrawal-proofs/" + request.getId(), new byte[]{1, 2, 3}, "image/png");
        verify(ledger).recordEntry(eq(request.getTeacherId()), eq("PAYOUT"), eq(request.getTotalAmount()),
                isNull(), isNull(), eq(request.getId()), contains("bằng chứng"), eq(staffId));
        verify(notifications).notifyWithdrawalPaid(request.getId(), request.getTeacherId(), request.getTotalAmount(), null);
        assertThrows(AppError.class, () -> service.markPaidWithProof(request.getId(), staffId, png()));
        verify(requests).save(request);
        verifyNoMoreInteractions(storage, ledger);
    }

    @Test
    void invalidImagesNeverUploadOrPay() {
        assertThrows(AppError.class, () -> service.markPaidWithProof(request.getId(), staffId, null));
        for (var file : List.of(new MockMultipartFile("file", new byte[0]),
                new MockMultipartFile("file", "proof.pdf", "application/pdf", new byte[]{1}),
                new MockMultipartFile("file", "proof.png", "image/png", new byte[5 * 1024 * 1024 + 1]))) {
            assertThrows(AppError.class, () -> service.markPaidWithProof(request.getId(), staffId, file));
        }
        assertEquals(WithdrawalStatus.APPROVED, request.getStatus());
        verify(requests, never()).save(any());
        verifyNoInteractions(storage, ledger, notifications);
    }

    @Test
    void uploadFailureKeepsApprovedAndDoesNotRecordPayout() {
        doThrow(new IllegalStateException("storage unavailable")).when(storage).uploadBytes(anyString(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> service.markPaidWithProof(request.getId(), staffId, png()));
        assertEquals(WithdrawalStatus.APPROVED, request.getStatus());
        verify(requests, never()).save(any());
        verifyNoInteractions(ledger, notifications);
    }

    @Test
    void onlyApprovedRequestsCanBePaid() {
        for (WithdrawalStatus status : List.of(WithdrawalStatus.PENDING, WithdrawalStatus.REJECTED, WithdrawalStatus.PAID)) {
            request.setStatus(status);
            assertThrows(AppError.class, () -> service.markPaidWithProof(request.getId(), staffId, png()));
        }
        verifyNoInteractions(storage, ledger, notifications);
    }

    @Test
    void onlyOwnerAndReviewersCanReadTheStoredProof() {
        request.setProofImageUrl("/api/v1/teacher/finance/withdrawals/" + request.getId() + "/proof");
        assertEquals("FORBIDDEN", assertThrows(AppError.class,
                () -> service.proof(request.getId(), UUID.randomUUID(), false)).getErrorCode());
        verifyNoInteractions(storage);
        service.proof(request.getId(), request.getTeacherId(), false);
        service.proof(request.getId(), staffId, true);
        verify(storage, times(2)).getObject("withdrawal-proofs/" + request.getId());
        request.setProofImageUrl("https://external.test/proof.png");
        assertEquals("PROOF_NOT_FOUND", assertThrows(AppError.class,
                () -> service.proof(request.getId(), request.getTeacherId(), false)).getErrorCode());
        verifyNoMoreInteractions(storage);
    }
}
