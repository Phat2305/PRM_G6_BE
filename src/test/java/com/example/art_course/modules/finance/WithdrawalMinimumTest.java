package com.example.art_course.modules.finance;

import com.example.art_course.common.AppError;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.withdrawal.dto.CreateWithdrawalRequest;
import com.example.art_course.modules.withdrawal.entity.WithdrawalRequest;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// FIN-04: kiểm tra mức tối thiểu tại DTO và service, không kết nối database.
class WithdrawalMinimumTest {
    private final WithdrawalRequestRepository requests = mock(WithdrawalRequestRepository.class);
    private final MoneyLedgerRepository balances = mock(MoneyLedgerRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final WithdrawalService service = new WithdrawalService(requests, balances, ledger,
            mock(NotificationService.class), mock(com.example.art_course.video.service.R2StorageService.class));
    private final UUID teacherId = UUID.randomUUID();

    private CreateWithdrawalRequest request(BigDecimal amount) {
        return new CreateWithdrawalRequest(amount, "VCB - 0012345678");
    }

    @Test
    void dtoRejectsBelowMinimumAndAcceptsTenThousand() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (BigDecimal amount : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-1"),
                    new BigDecimal("9999"), new BigDecimal("9999.99"))) {
                assertFalse(validator.validate(request(amount)).isEmpty(), "amount=" + amount);
            }
            assertTrue(validator.validate(request(new BigDecimal("10000"))).isEmpty());
            assertTrue(validator.validate(request(new BigDecimal("40000"))).isEmpty());
        }
    }

    @Test
    void serviceRejectsBelowMinimumBeforeCreatingRequestOrLockingMoney() {
        for (BigDecimal amount : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-1"),
                new BigDecimal("9999"))) {
            AppError error = assertThrows(AppError.class,
                    () -> service.requestWithdrawal(teacherId, request(amount)));
            assertEquals("INVALID_AMOUNT", error.getErrorCode());
            assertEquals(400, error.getStatus().value());
        }
        verifyNoInteractions(requests, balances, ledger);
    }

    @Test
    void tenThousandCreatesRequestAndLocksExactlyThatAmount() {
        BigDecimal amount = new BigDecimal("10000");
        when(balances.calculateAvailableBalance(teacherId)).thenReturn(amount);
        when(requests.save(any(WithdrawalRequest.class))).thenAnswer(invocation -> {
            WithdrawalRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.requestWithdrawal(teacherId, request(amount));

        assertEquals(amount, result.getTotalAmount());
        verify(ledger).recordEntry(eq(teacherId), eq("WITHDRAW_LOCK"), eq(amount),
                isNull(), isNull(), eq(result.getId()), anyString(), eq(teacherId));
    }
}
