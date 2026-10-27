package com.example.art_course.user.Controller;

import com.example.art_course.common.AppError;
import com.example.art_course.user.dto.ChangePasswordRequest;
import com.example.art_course.user.dto.ProfileResponse;
import com.example.art_course.user.dto.UpdateProfileRequest;
import com.example.art_course.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

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

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadAvatar(@RequestParam("file") MultipartFile file, Authentication auth) {
        userService.uploadAvatar(currentUserId(auth), file);
    }

    @GetMapping("/me/avatar")
    public ResponseEntity<Resource> avatar(Authentication auth) {
        ResponseInputStream<GetObjectResponse> object = userService.avatar(currentUserId(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.response().contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .body(new InputStreamResource(object));
    }

    @PostMapping(value = "/me/certificates/{index}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadCertificate(@PathVariable int index, @RequestParam("file") MultipartFile file,
                                  Authentication auth) {
        userService.uploadCertificateFile(currentUserId(auth), index, file);
    }

    @GetMapping("/{userId}/certificates/{index}/file")
    public ResponseEntity<Resource> certificate(@PathVariable UUID userId, @PathVariable int index,
                                                Authentication auth) {
        ResponseInputStream<GetObjectResponse> object =
                userService.certificateFile(currentUserId(auth), userId, index);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.response().contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .body(new InputStreamResource(object));
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
