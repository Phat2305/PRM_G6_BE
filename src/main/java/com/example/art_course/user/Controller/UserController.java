package com.example.art_course.user.Controller;

import com.example.art_course.common.AppError;
import com.example.art_course.user.dto.ChangePasswordRequest;
import com.example.art_course.user.dto.ProfileResponse;
import com.example.art_course.user.dto.UpdateProfileRequest;
import com.example.art_course.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ProfileResponse me(Authentication auth) {
        return userService.getProfile(currentUserId(auth));
    }

    @PutMapping("/me")
    public ProfileResponse updateMe(@Valid @RequestBody UpdateProfileRequest request, Authentication auth) {
        return userService.updateProfile(currentUserId(auth), request);
    }

    @PostMapping("/me/logout")
    public void logout(Authentication auth) {
        userService.revokeTokens(currentUserId(auth));
    }

    @PostMapping("/me/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication auth) {
        userService.changePassword(currentUserId(auth), request);
    }

    private UUID currentUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UUID id)) {
            throw new AppError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token không hợp lệ");
        }
        return id;
    }
}
