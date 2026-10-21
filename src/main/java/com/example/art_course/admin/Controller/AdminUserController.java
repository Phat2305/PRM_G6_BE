package com.example.art_course.admin.Controller;

import com.example.art_course.admin.dto.CreateStaffRequest;
import com.example.art_course.admin.dto.StaffResponse;
import com.example.art_course.admin.dto.StaffStatusRequest;
import com.example.art_course.admin.dto.UpdateStaffRequest;
import com.example.art_course.admin.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

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
    @GetMapping("/teachers/pending")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> approveTeacher(
            @PathVariable UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        if (!"TEACHER".equals(user.getRole())
                || !"PENDING".equals(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is not a pending teacher"
            );
        }

        user.setStatus("ACTIVE");
        userRepository.save(user);

        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole(),
                "status", user.getStatus()
        );
    }
}
