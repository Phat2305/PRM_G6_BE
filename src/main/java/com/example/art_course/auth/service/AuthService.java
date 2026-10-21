
package com.example.art_course.auth.service;

import com.example.art_course.auth.dto.AuthResponse;
import com.example.art_course.auth.dto.LoginRequest;
import com.example.art_course.auth.dto.RegisterRequest;
import com.example.art_course.auth.dto.RegisterRole;
import com.example.art_course.auth.repository.AuthRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.auth.security.JwtTokenProvider;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import com.example.art_course.auth.security.JwtTokenProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthRepository authRepository;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AuthRepository authRepository) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authRepository = authRepository;
    }

    // Register
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();

        String email = PasswordResetService.normalize(request.getEmail());

        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username or email already exists"
            );
        }
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        user.setRole(request.getRole().name());
        user.setStatus(request.getRole().initialStatus());
<<<<<<< Updated upstream
        // saveAndFlush để lỗi UNIQUE (đăng ký trùng username cùng lúc) trả 409 thay vì 500
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
        }
         
        // Họ tên nằm ở bảng profile theo role
=======

        user = userRepository.saveAndFlush(user);

        // Họ tên nằm ở bảng profile theo role; teacher chưa có thông tin ngân hàng khi đăng ký
>>>>>>> Stashed changes
        String fullName = request.getFullName().trim();
        if (request.getRole() == RegisterRole.TEACHER) {
            authRepository.insertTeacherProfile(user.getId(), fullName);
        } else {
            authRepository.insertParentProfile(user.getId(), fullName);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return toResponse(user, token);
    }

    // Login
    public AuthResponse login(LoginRequest request) {
        // Cùng một lỗi cho "sai username" và "sai password" để không lộ username nào tồn tại
        User user = userRepository
                .findByUsername(request.getUsername().trim())
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {
            throw invalidCredentials();
        }

        // Chỉ tài khoản ACTIVE được cấp token (giáo viên PENDING chưa được duyệt, tài khoản bị khóa)
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AppError(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE",
                    "Tài khoản chưa được kích hoạt hoặc đã bị khóa"
            );
        }

        // Generate JWT containing userId, username and role
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        return toResponse(user, token);
    }

    private AuthResponse toResponse(User user, String token) {
        AuthResponse response = new AuthResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setToken(token);
        return response;
    }

    private AppError invalidCredentials() {
        return new AppError(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Username hoặc password không đúng"
        );
    }
}
