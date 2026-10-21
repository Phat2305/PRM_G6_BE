package com.example.art_course.auth.service;

import com.example.art_course.auth.dto.ForgotPasswordRequest;
import com.example.art_course.auth.dto.ResetPasswordRequest;
import com.example.art_course.auth.repository.PasswordResetRepository;
import com.example.art_course.auth.repository.PasswordResetRepository.Entry;
import com.example.art_course.common.AppError;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
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
        String email = normalize(request.getEmail());
        if (userRepository.findByEmail(email).isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        boolean tooSoon = resetRepository.find(email)
                .filter(e -> e.sentAt.plus(RESEND_COOLDOWN).isAfter(now))
                .isPresent();
        if (tooSoon) {
            return;
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        resetRepository.save(email, new Entry(passwordEncoder.encode(code), now, now.plus(CODE_TTL)));
        mailService.send(email, "Mã đặt lại mật khẩu",
                "Mã xác nhận của bạn là " + code + ". Mã có hiệu lực trong 10 phút. "
                        + "Nếu bạn không yêu cầu, hãy bỏ qua email này.");
    }

    public synchronized void resetPassword(ResetPasswordRequest request) {
        String email = normalize(request.getEmail());
        Entry entry = resetRepository.find(email).orElseThrow(this::invalidCode);

        if (entry.expiresAt.isBefore(Instant.now())) {
            resetRepository.delete(email);
            throw invalidCode();
        }
        if (!passwordEncoder.matches(request.getCode(), entry.codeHash)) {
            // Sai quá số lần cho phép thì hủy mã, phải xin mã mới
            if (++entry.failedAttempts >= MAX_FAILED_ATTEMPTS) {
                resetRepository.delete(email);
            }
            throw invalidCode();
        }

        User user = userRepository.findByEmail(email).orElseThrow(this::invalidCode);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.revokeTokens();
        userRepository.save(user);
        resetRepository.delete(email);
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    // Một lỗi chung cho email sai, mã sai, mã hết hạn
    private AppError invalidCode() {
        return new AppError(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE", "Mã xác nhận không đúng hoặc đã hết hạn");
    }
}
