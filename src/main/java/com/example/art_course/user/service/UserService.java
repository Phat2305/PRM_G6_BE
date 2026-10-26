package com.example.art_course.user.service;

import com.example.art_course.auth.repository.AuthRepository;
import com.example.art_course.auth.service.PasswordResetService;
import com.example.art_course.auth.repository.AuthRepository.ProfileRow;
import com.example.art_course.common.AppError;
import com.example.art_course.user.dto.ChangePasswordRequest;
import com.example.art_course.user.dto.ProfileResponse;
import com.example.art_course.user.dto.UpdateProfileRequest;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.art_course.video.service.R2StorageService;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

// Hồ sơ và mật khẩu của chính người đăng nhập
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final R2StorageService r2StorageService;

    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
    private static final Set<String> AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public ProfileResponse getProfile(UUID userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        boolean emailGiven = request.getEmail() != null;
        if (emailGiven) {
            setEmail(user, request.getEmail());
        }
        int updated = switch (user.getRole()) {
            case "PARENT" -> authRepository.updateParentProfile(
                    userId, request.getFullName(), request.getShippingAddress());
            case "TEACHER" -> authRepository.updateTeacherProfile(
                    userId, request.getFullName(), request.getBankAccountInfo(),
                    request.getPhone(), request.getBio(), joinCertificates(request.getCertificates()));
            default -> 0;
        };
        // Staff/Admin không có bảng hồ sơ nhưng vẫn được cập nhật email
        if (updated == 0 && !emailGiven) {
            throw new AppError(HttpStatus.BAD_REQUEST, "NO_PROFILE", "Tài khoản này không có hồ sơ để cập nhật");
        }
        return toResponse(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        // 400 chứ không phải 401, để app không hiểu nhầm là hết phiên và tự đăng xuất
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AppError(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Mật khẩu hiện tại không đúng");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.revokeTokens();
        userRepository.save(user);
    }

    // Đăng xuất khỏi mọi thiết bị: mọi token cấp trước giờ này không dùng được nữa
    @Transactional
    public void revokeTokens(UUID userId) {
        User user = findUser(userId);
        user.revokeTokens();
        userRepository.save(user);
    }

    // Ảnh lưu ở R2 theo key cố định nên không cần cột trong DB; tải lại là ghi đè.
    public void uploadAvatar(UUID userId, MultipartFile file) {
        findUser(userId);
        if (file == null || file.isEmpty()) {
            throw new AppError(HttpStatus.BAD_REQUEST, "AVATAR_REQUIRED", "Chưa chọn ảnh");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new AppError(HttpStatus.CONTENT_TOO_LARGE, "AVATAR_TOO_LARGE", "Ảnh tối đa 5 MB");
        }
        String type = file.getContentType();
        if (type == null || !AVATAR_TYPES.contains(type)) {
            throw new AppError(HttpStatus.BAD_REQUEST, "AVATAR_TYPE", "Chỉ nhận ảnh JPG, PNG hoặc WEBP");
        }
        try {
            r2StorageService.uploadBytes(avatarKey(userId), file.getBytes(), type);
        } catch (IOException e) {
            throw new AppError(HttpStatus.BAD_REQUEST, "AVATAR_READ", "Không đọc được ảnh");
        }
    }

    // Chưa có ảnh thì R2StorageService ném 404
    public ResponseInputStream<GetObjectResponse> avatar(UUID userId) {
        return r2StorageService.getObject(avatarKey(userId));
    }

    private static String avatarKey(UUID userId) {
        return "avatars/" + userId;
    }

    private void setEmail(User user, String rawEmail) {
        String email = PasswordResetService.normalize(rawEmail);
        if (email.equals(user.getEmail())) {
            return;
        }
        if (userRepository.existsByEmail(email)) {
            throw new AppError(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email đã được dùng bởi tài khoản khác");
        }
        user.setEmail(email);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new AppError(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email đã được dùng bởi tài khoản khác");
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }

    private ProfileResponse toResponse(User u) {
        ProfileRow p = (switch (u.getRole()) {
            case "PARENT" -> authRepository.findParentProfile(u.getId());
            case "TEACHER" -> authRepository.findTeacherProfile(u.getId());
            default -> java.util.Optional.<ProfileRow>empty();
        }).orElse(new ProfileRow(null, null, null));
        return new ProfileResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.getStatus(),
                p.fullName(), p.shippingAddress(), p.bankAccountInfo(),
                p.phone(), p.bio(), splitCertificates(p.certificatesText()));
    }

    // Chứng chỉ lưu trong certificates_text, mỗi dòng một chứng chỉ; null = giữ nguyên, rỗng = xóa hết.
    private static String joinCertificates(java.util.List<String> certificates) {
        if (certificates == null) {
            return null;
        }
        return certificates.stream().map(String::trim).filter(c -> !c.isEmpty())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static java.util.List<String> splitCertificates(String text) {
        if (text == null || text.isBlank()) {
            return java.util.List.of();
        }
        return text.lines().map(String::trim).filter(c -> !c.isEmpty()).toList();
    }
}
