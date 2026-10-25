package com.example.art_course.admin.Controller;

import com.example.art_course.admin.dto.CreateStaffRequest;
import com.example.art_course.admin.dto.RejectTeacherRequest;
import com.example.art_course.admin.dto.StaffResponse;
import com.example.art_course.admin.dto.StaffStatusRequest;
import com.example.art_course.admin.dto.UpdateStaffRequest;
import com.example.art_course.admin.service.AdminUserService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserRepository userRepository;

    @PostMapping("/staff")
    public StaffResponse createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return adminUserService.createStaff(request);
    }

    @GetMapping("/staff")
    public List<StaffResponse> getStaffAccounts() {
        return adminUserService.listStaff();
    }

    @PutMapping("/staff/{id}")
    public StaffResponse updateStaff(@PathVariable UUID id, @Valid @RequestBody UpdateStaffRequest request) {
        return adminUserService.updateStaff(id, request);
    }

    @PatchMapping("/staff/{id}/status")
    public StaffResponse setStaffStatus(@PathVariable UUID id, @Valid @RequestBody StaffStatusRequest request) {
        return adminUserService.setStatus(id, request);
    }

    @DeleteMapping("/staff/{id}")
    public void deleteStaff(@PathVariable UUID id) {
        adminUserService.deleteStaff(id);
    }
    // ACC-12: Staff (và Admin) duyệt giáo viên mới đăng ký.
    @GetMapping("/teachers/pending")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public List<Map<String, Object>> getPendingTeachers() {
        return userRepository.findAll().stream()
                .filter(user ->
                        "TEACHER".equals(user.getRole())
                                && "PENDING".equals(user.getStatus()))
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "role", user.getRole(),
                        "status", user.getStatus()
                ))
                .toList();
    }

    @PatchMapping("/teachers/{userId}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public Map<String, Object> approveTeacher(@PathVariable UUID userId, Authentication auth) {
        return toMap(adminUserService.decideTeacher(userId, UUID.fromString(auth.getName()), "ACTIVE", "APPROVED", null));
    }

    // Từ chối giáo viên mới đăng ký: đặt status của user thành REJECTED và ghi lý do vào nhật ký kiểm duyệt.
    @PatchMapping("/teachers/{userId}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public Map<String, Object> rejectTeacher(
            @PathVariable UUID userId,
            @Valid @RequestBody RejectTeacherRequest request,
            Authentication auth) {
        return toMap(adminUserService.decideTeacher(
                userId, UUID.fromString(auth.getName()), "REJECTED", "REJECTED", request.getReason()));
    }

    private static Map<String, Object> toMap(User user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole(),
                "status", user.getStatus()
        );
    }
}
