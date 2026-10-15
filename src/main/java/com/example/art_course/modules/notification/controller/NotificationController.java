package com.example.art_course.modules.notification.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/my-notifications")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Notification>> getMyNotifications(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ApiResponse.ok(notificationService.getMyNotifications(userId));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> markAsRead(@PathVariable UUID id, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        notificationService.markAsRead(id, userId);
        return ApiResponse.ok("Đã đánh dấu đã đọc", null);
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Integer> markAllAsRead(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        int count = notificationService.markAllAsRead(userId);
        return ApiResponse.ok("Đã đánh dấu tất cả đã đọc", count);
    }
}
