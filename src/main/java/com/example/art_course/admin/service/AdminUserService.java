package com.example.art_course.admin.service;

import com.example.art_course.admin.dto.CreateStaffRequest;
import com.example.art_course.admin.dto.StaffResponse;
import com.example.art_course.admin.dto.StaffStatusRequest;
import com.example.art_course.admin.dto.UpdateStaffRequest;
import com.example.art_course.common.AppError;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public StaffResponse createStaff(CreateStaffRequest request) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw usernameTaken();
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("STAFF");
        user.setStatus("ACTIVE");
        return toResponse(saveUnique(user));
    }

    public List<StaffResponse> listStaff() {
        return userRepository.findAll().stream()
                .filter(u -> "STAFF".equals(u.getRole()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StaffResponse updateStaff(UUID id, UpdateStaffRequest request) {
        User user = findStaff(id);

        String username = request.getUsername();
        if (username != null && !username.isBlank() && !username.trim().equals(user.getUsername())) {
            // Chỉ đổi hoa/thường tên của chính mình thì không coi là trùng
            if (!username.trim().equalsIgnoreCase(user.getUsername())
                    && userRepository.existsByUsernameIgnoreCase(username.trim())) {
                throw usernameTaken();
            }
            user.setUsername(username.trim());
        }

        String password = request.getPassword();
        if (password != null && !password.isBlank()) {
            if (password.length() < 6 || password.length() > 20) {
                throw new AppError(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "Password gồm 6-20 ký tự");
            }
            user.setPassword(passwordEncoder.encode(password));
        }
        return toResponse(saveUnique(user));
    }

    // Login chỉ cấp token cho ACTIVE; token đã cấp vẫn dùng được tới khi hết hạn
    @Transactional
    public StaffResponse setStatus(UUID id, StaffStatusRequest request) {
        String status = request.getStatus().trim().toUpperCase();
        if (!status.equals("ACTIVE") && !status.equals("LOCKED")) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "status phải là ACTIVE hoặc LOCKED");
        }
        User user = findStaff(id);
        user.setStatus(status);
        // Khóa thì thu hồi luôn token cũ, nếu không mở khóa lại trong ngày sẽ "sống lại" các token đó
        if (status.equals("LOCKED")) {
            user.revokeTokens();
        }
        return toResponse(userRepository.save(user));
    }

    // Staff đã có nhật ký duyệt thì không xóa được (FK) -> khóa tài khoản thay vì xóa
    public void deleteStaff(UUID id) {
        User user = findStaff(id);
        try {
            userRepository.delete(user);
        } catch (DataIntegrityViolationException e) {
            throw new AppError(HttpStatus.CONFLICT, "STAFF_IN_USE",
                    "Staff đã có dữ liệu liên quan, hãy khóa tài khoản thay vì xóa");
        }
    }

    private User findStaff(UUID id) {
        return userRepository.findById(id)
                .filter(u -> "STAFF".equals(u.getRole()))
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND", "Không tìm thấy staff"));
    }

    // saveAndFlush để lỗi UNIQUE (hai yêu cầu cùng username cùng lúc) nổ ngay tại đây chứ không phải lúc commit
    private User saveUnique(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw usernameTaken();
        }
    }

    private AppError usernameTaken() {
        return new AppError(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username đã tồn tại");
    }

    private StaffResponse toResponse(User u) {
        return new StaffResponse(u.getId(), u.getUsername(), u.getStatus());
    }
}
