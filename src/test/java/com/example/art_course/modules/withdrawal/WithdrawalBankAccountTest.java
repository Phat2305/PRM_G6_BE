package com.example.art_course.modules.withdrawal;

import com.example.art_course.auth.repository.AuthRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.modules.withdrawal.service.WithdrawalBankAccount;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// FIN-04: tiền rút chỉ về tài khoản trong hồ sơ giáo viên.
class WithdrawalBankAccountTest {
    private final AuthRepository auth = mock(AuthRepository.class);
    private final WithdrawalBankAccount bankAccount = new WithdrawalBankAccount(auth);
    private final UUID teacherId = UUID.randomUUID();

    private void profile(String bank) {
        when(auth.findTeacherProfile(teacherId))
                .thenReturn(Optional.of(new AuthRepository.ProfileRow("Nguyễn Thị Lan", null, bank)));
    }

    @Test
    void usesTheProfileAccountWhenNothingIsSent() {
        profile("VCB - 0123456789 - NGUYEN THI LAN");
        assertEquals("VCB - 0123456789 - NGUYEN THI LAN", bankAccount.resolve(teacherId, null));
        assertEquals("VCB - 0123456789 - NGUYEN THI LAN", bankAccount.resolve(teacherId, "  "));
    }

    @Test
    void sameAccountSentByAnOlderClientIsAcceptedEvenIfCaseOrAccentsDiffer() {
        profile("VCB - 0123456789 - NGUYEN THI LAN");
        assertEquals("VCB - 0123456789 - NGUYEN THI LAN",
                bankAccount.resolve(teacherId, "vcb - 0123456789 - Nguyễn Thị Lan"));
    }

    @Test
    void anotherAccountIsRejected() {
        profile("VCB - 0123456789 - NGUYEN THI LAN");
        AppError e = assertThrows(AppError.class,
                () -> bankAccount.resolve(teacherId, "VCB - 9999999999 - NGUYEN THI LAN"));
        assertEquals("BANK_ACCOUNT_MISMATCH", e.getErrorCode());
        assertEquals(400, e.getStatus().value());
    }

    @Test
    void emptyProfileAccountBlocksWithdrawing() {
        profile("");
        assertEquals("BANK_ACCOUNT_REQUIRED",
                assertThrows(AppError.class, () -> bankAccount.resolve(teacherId, "VCB - 0123456789 - A B")).getErrorCode());
        when(auth.findTeacherProfile(teacherId)).thenReturn(Optional.empty());
        assertEquals("BANK_ACCOUNT_REQUIRED",
                assertThrows(AppError.class, () -> bankAccount.resolve(teacherId, null)).getErrorCode());
    }

    @Test
    void oldFreeTextProfileAccountMustBeUpdatedFirst() {
        profile("VCB 0123 LAN");
        assertEquals("BANK_ACCOUNT_INVALID",
                assertThrows(AppError.class, () -> bankAccount.resolve(teacherId, null)).getErrorCode());
    }
}
