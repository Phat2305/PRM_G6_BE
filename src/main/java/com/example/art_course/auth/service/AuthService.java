
package com.example.art_course.auth.service;

import com.example.art_course.auth.dto.AuthResponse;
import com.example.art_course.auth.dto.LoginRequest;
import com.example.art_course.auth.dto.RegisterRequest;
import com.example.art_course.auth.security.JwtTokenProvider;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // Register
    public AuthResponse register(RegisterRequest request) {

        if (request.getUsername() == null
                || request.getUsername().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username is required"
            );
        }

        if (request.getPassword() == null
                || request.getPassword().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password is required"
            );
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        if (request.getRole() == null
                || request.getRole().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role is required"
            );
        }

        String requestedRole = request.getRole()
                .trim()
                .toUpperCase();

        String role;
        String status;

        switch (requestedRole) {
            case "PARENT" -> {
                role = "PARENT";
                status = "ACTIVE";
            }
            case "TEACHER" -> {
                // Chưa cấp quyền giáo viên khi tự đăng ký
                role = "USER";
                status = "PENDING";
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role must be PARENT or TEACHER"
            );
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        user.setRole(role);
        user.setStatus(status);

        userRepository.save(user);

        AuthResponse response = new AuthResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());

        return response;
    }

    // Login
    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() ->
                        new RuntimeException("Username not found"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new RuntimeException("Invalid password");
        }

        // Generate JWT containing username and role
        String token = jwtTokenProvider.generateToken(
                user.getUsername(),
                user.getRole()
        );

        AuthResponse response = new AuthResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setToken(token);

        return response;
    }
}
