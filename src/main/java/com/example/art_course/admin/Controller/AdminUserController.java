
package com.example.art_course.admin.Controller;

import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ADMIN tạo tài khoản STAFF
    @PostMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public User createStaff(@RequestBody Map<String, String> body) {

        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username and password are required"
            );
        }

        username = username.trim();

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("STAFF");
        user.setStatus("ACTIVE");

        return userRepository.save(user);
    }

    // ADMIN xem danh sách tài khoản STAFF
    @GetMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getStaffAccounts() {
        return userRepository.findAll()
                .stream()
                .filter(user -> "STAFF".equals(user.getRole()))
                .toList();
    }
}
