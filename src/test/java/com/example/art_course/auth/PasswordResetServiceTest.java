package com.example.art_course.auth;

import com.example.art_course.auth.dto.ForgotPasswordRequest;
import com.example.art_course.auth.dto.ResetPasswordRequest;
import com.example.art_course.auth.repository.PasswordResetRepository;
import com.example.art_course.auth.service.MailService;
import com.example.art_course.auth.service.PasswordResetService;
import com.example.art_course.common.AppError;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private static final String EMAIL = "an@example.com";

    private final UserRepository users = mock(UserRepository.class);
    private final MailService mail = mock(MailService.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final PasswordResetRepository store = new PasswordResetRepository();
    private final PasswordResetService service = new PasswordResetService(users, store, encoder, mail);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User(UUID.randomUUID(), "an", encoder.encode("oldpassword"), "PARENT", "ACTIVE", EMAIL, null);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private ForgotPasswordRequest forgot(String email) {
        ForgotPasswordRequest r = new ForgotPasswordRequest();
        r.setEmail(email);
        return r;
    }

    private ResetPasswordRequest reset(String code) {
        ResetPasswordRequest r = new ResetPasswordRequest();
        r.setEmail(EMAIL);
        r.setCode(code);
        r.setNewPassword("newpassword1");
        r.setConfirmPassword("newpassword1");
        return r;
    }

    // Gửi mã và đọc lại 6 số từ nội dung mail
    private String requestAndReadCode() {
        service.requestCode(forgot(EMAIL));
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(EMAIL), any(), text.capture());
        Matcher m = Pattern.compile("\\d{6}").matcher(text.getValue());
        assertTrue(m.find());
        return m.group();
    }

    @Test
    void resetWithMismatchedConfirmation_isRejected_andKeepsTheCodeUsable() {
        String code = requestAndReadCode();
        ResetPasswordRequest request = reset(code);
        request.setConfirmPassword("something-else");

        AppError e = assertThrows(AppError.class, () -> service.resetPassword(request));

        assertEquals("PASSWORD_MISMATCH", e.getErrorCode());
        verify(users, never()).save(any());
        // Mã chưa bị tiêu hao: nhập lại đúng thì đổi được.
        service.resetPassword(reset(code));
        verify(users).save(user);
    }

    @Test
    void unknownEmail_sendsNothing_andDoesNotFail() {
        when(users.findByEmail("x@example.com")).thenReturn(Optional.empty());

        service.requestCode(forgot("x@example.com"));

        verifyNoInteractions(mail);
    }

    @Test
    void secondRequestInsideCooldown_sendsOnlyOneMail() {
        service.requestCode(forgot(EMAIL));
        service.requestCode(forgot("  " + EMAIL.toUpperCase() + " "));

        verify(mail, times(1)).send(eq(EMAIL), any(), any());
    }

    @Test
    void correctCode_changesPassword_andCannotBeReused() {
        String code = requestAndReadCode();

        service.resetPassword(reset(code));

        assertTrue(encoder.matches("newpassword1", user.getPassword()));
        verify(users).save(user);
        AppError e = assertThrows(AppError.class, () -> service.resetPassword(reset(code)));
        assertEquals("INVALID_RESET_CODE", e.getErrorCode());
    }

    @Test
    void fiveWrongCodes_burnTheCode_evenIfNextTryIsCorrect() {
        String code = requestAndReadCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThrows(AppError.class, () -> service.resetPassword(reset(wrong)));
        }

        assertThrows(AppError.class, () -> service.resetPassword(reset(code)));
        verify(users, never()).save(any());
    }

    @Test
    void expiredCode_isRejected() {
        String code = requestAndReadCode();
        store.save(EMAIL, new PasswordResetRepository.Entry(
                encoder.encode(code), Instant.now().minusSeconds(900), Instant.now().minusSeconds(1)));

        AppError e = assertThrows(AppError.class, () -> service.resetPassword(reset(code)));

        assertEquals("INVALID_RESET_CODE", e.getErrorCode());
        verify(users, never()).save(any());
    }
}
