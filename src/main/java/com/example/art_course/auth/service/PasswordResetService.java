package com.example.art_course.auth.service;

import com.example.art_course.auth.dto.ForgotPasswordRequest;
import com.example.art_course.auth.dto.ResetPasswordRequest;
import com.example.art_course.auth.repository.PasswordResetRepository;
import com.example.art_course.auth.repository.PasswordResetRepository.Entry;
import com.example.art_course.common.AppError;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordResetRepository resetRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();

    // Im lặng khi email chưa đăng ký hoặc đang trong thời gian chờ, để không lộ email nào có tài khoản
    public synchronized void requestCode(ForgotPasswordRequest request) {
        long startedAt = System.nanoTime();
        String email = normalize(request.getEmail());
        log.debug("Password reset code requested email={}", maskEmail(email));
        if (userRepository.findByEmail(email).isEmpty()) {
            log.debug("Password reset request ignored email={} reason=unknown_email elapsedMs={}",
                    maskEmail(email), elapsedMs(startedAt));
            return;
        }
        Instant now = Instant.now();
        boolean tooSoon = resetRepository.find(email)
                .filter(e -> e.sentAt.plus(RESEND_COOLDOWN).isAfter(now))
                .isPresent();
        if (tooSoon) {
            log.debug("Password reset request ignored email={} reason=resend_cooldown elapsedMs={}",
                    maskEmail(email), elapsedMs(startedAt));
            return;
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        resetRepository.save(email, new Entry(passwordEncoder.encode(code), now, now.plus(CODE_TTL)));
        mailService.send(email, "Mã đặt lại mật khẩu",
                "Mã xác nhận của bạn là " + code + ". Mã có hiệu lực trong 10 phút. "
                        + "Nếu bạn không yêu cầu, hãy bỏ qua email này.");
        log.debug("Password reset code sent email={} expiresInMinutes={} elapsedMs={}",
                maskEmail(email), CODE_TTL.toMinutes(), elapsedMs(startedAt));
    }

    public synchronized void resetPassword(ResetPasswordRequest request) {
        long startedAt = System.nanoTime();
        String email = normalize(request.getEmail());
        log.debug("Password reset submitted email={}", maskEmail(email));
        // Xác nhận mật khẩu phải khớp: kiểm tra trước, không tính vào số lần nhập sai mã.
        if (!java.util.Objects.equals(request.getNewPassword(), request.getConfirmPassword())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Mật khẩu xác nhận không khớp");
        }
        Entry entry = resetRepository.find(email).orElseThrow(() -> {
            log.debug("Password reset rejected email={} reason=no_reset_record elapsedMs={}",
                    maskEmail(email), elapsedMs(startedAt));
            return invalidCode();
        });

        if (entry.expiresAt.isBefore(Instant.now())) {
            resetRepository.delete(email);
            log.debug("Password reset rejected email={} reason=expired_code elapsedMs={}",
                    maskEmail(email), elapsedMs(startedAt));
            throw invalidCode();
        }
        if (!passwordEncoder.matches(request.getCode(), entry.codeHash)) {
            // Sai quá số lần cho phép thì hủy mã, phải xin mã mới
            if (++entry.failedAttempts >= MAX_FAILED_ATTEMPTS) {
                resetRepository.delete(email);
                log.debug("Password reset rejected email={} reason=invalid_code attempts={} "
                                + "code_deleted elapsedMs={}",
                        maskEmail(email), entry.failedAttempts, elapsedMs(startedAt));
            } else {
                log.debug("Password reset rejected email={} reason=invalid_code attempts={} elapsedMs={}",
                        maskEmail(email), entry.failedAttempts, elapsedMs(startedAt));
            }
            throw invalidCode();
        }

        User user = userRepository.findByEmail(email).orElseThrow(() -> {
            log.debug("Password reset rejected email={} reason=user_not_found elapsedMs={}",
                    maskEmail(email), elapsedMs(startedAt));
            return invalidCode();
        });
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.revokeTokens();
        userRepository.save(user);
        resetRepository.delete(email);
        log.debug("Password reset succeeded email={} elapsedMs={}",
                maskEmail(email), elapsedMs(startedAt));
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    // Một lỗi chung cho email sai, mã sai, mã hết hạn
    private AppError invalidCode() {
        return new AppError(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE", "Mã xác nhận không đúng hoặc đã hết hạn");
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String maskedLocalPart = localPart.length() == 1
                ? "*"
                : localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1);
        return maskedLocalPart + domain;
    }
}
