package com.example.art_course.user;

import com.example.art_course.admin.dto.StaffStatusRequest;
import com.example.art_course.admin.service.AdminUserService;
import com.example.art_course.auth.repository.AuthRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.user.dto.ChangePasswordRequest;
import com.example.art_course.user.dto.UpdateProfileRequest;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import com.example.art_course.user.service.UserService;
import com.example.art_course.admin.dto.CreateStaffRequest;
import com.example.art_course.video.service.R2StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserAccountTest {

    private final UserRepository repo = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthRepository authRepo = mock(AuthRepository.class);
    private final R2StorageService r2 = mock(R2StorageService.class);
    private final UserService userService = new UserService(repo, authRepo, encoder, r2);
    private final AdminUserService adminService = new AdminUserService(repo, encoder);

    private User user(String role) {
        User u = new User(UUID.randomUUID(), "u", "hash", role, "ACTIVE", null, null);
        when(repo.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private ChangePasswordRequest pw(String current, String next) {
        ChangePasswordRequest r = new ChangePasswordRequest();
        r.setCurrentPassword(current);
        r.setNewPassword(next);
        return r;
    }

    private StaffStatusRequest status(String s) {
        StaffStatusRequest r = new StaffStatusRequest();
        r.setStatus(s);
        return r;
    }

    @Test
    void changePassword_wrongCurrent_isRejectedAndNotSaved() {
        User u = user("PARENT");
        when(encoder.matches("bad", "hash")).thenReturn(false);

        AppError e = assertThrows(AppError.class, () -> userService.changePassword(u.getId(), pw("bad", "newpassword1")));

        assertEquals("WRONG_PASSWORD", e.getErrorCode());
        verify(repo, never()).save(any());
    }

    @Test
    void changePassword_ok_savesEncodedPassword() {
        User u = user("PARENT");
        when(encoder.matches("old", "hash")).thenReturn(true);
        when(encoder.encode("newpassword1")).thenReturn("newhash");

        userService.changePassword(u.getId(), pw("old", "newpassword1"));

        assertEquals("newhash", u.getPassword());
        verify(repo).save(u);
    }

    @Test
    void updateProfile_staffHasNoProfile_isRejected() {
        User u = user("STAFF");

        AppError e = assertThrows(AppError.class, () -> userService.updateProfile(u.getId(), new UpdateProfileRequest()));

        assertEquals("NO_PROFILE", e.getErrorCode());
    }

    @Test
    void updateProfile_parent_goesThroughRepository() {
        User u = user("PARENT");
        when(authRepo.updateParentProfile(u.getId(), "An", "HCM")).thenReturn(1);
        when(authRepo.findParentProfile(u.getId()))
                .thenReturn(Optional.of(new AuthRepository.ProfileRow("An", "HCM", null)));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("An");
        req.setShippingAddress("HCM");

        var res = userService.updateProfile(u.getId(), req);

        assertEquals("An", res.getFullName());
        assertEquals("HCM", res.getShippingAddress());
    }

    @Test
    void adminStaff_cannotTouchNonStaffAccount() {
        User parent = user("PARENT");

        AppError e = assertThrows(AppError.class, () -> adminService.setStatus(parent.getId(), status("LOCKED")));

        assertEquals("STAFF_NOT_FOUND", e.getErrorCode());
        assertEquals("ACTIVE", parent.getStatus());
    }

    @Test
    void adminStaff_lockAndInvalidStatus() {
        User staff = user("STAFF");
        when(repo.save(staff)).thenReturn(staff);

        assertEquals("LOCKED", adminService.setStatus(staff.getId(), status("locked")).getStatus());
        assertThrows(AppError.class, () -> adminService.setStatus(staff.getId(), status("BANNED")));
    }

    @Test
    void createStaff_usernameRace_returns409NotServerError() {
        when(repo.existsByUsernameIgnoreCase("nv1")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("h");
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("users_username_key"));
        CreateStaffRequest req = new CreateStaffRequest();
        req.setUsername("nv1");
        req.setPassword("password123");

        AppError e = assertThrows(AppError.class, () -> adminService.createStaff(req));

        assertEquals("USERNAME_TAKEN", e.getErrorCode());
    }

    @Test
    void logout_andPasswordChange_revokeOldTokens() {
        User u = user("PARENT");
        userService.revokeTokens(u.getId());
        assertNotNull(u.getTokensValidAfter());

        u.setTokensValidAfter(null);
        when(encoder.matches("old", "hash")).thenReturn(true);
        userService.changePassword(u.getId(), pw("old", "newpassword1"));
        assertNotNull(u.getTokensValidAfter());
    }

    @Test
    void uploadAvatar_rejectsNonImageAndMissingType_storesImageUnderUserKey() {
        User u = user("TEACHER");

        AppError text = assertThrows(AppError.class, () -> userService.uploadAvatar(u.getId(),
                new MockMultipartFile("file", "a.html", "text/html", new byte[]{1})));
        AppError noType = assertThrows(AppError.class, () -> userService.uploadAvatar(u.getId(),
                new MockMultipartFile("file", "a", null, new byte[]{1})));
        assertEquals("AVATAR_TYPE", text.getErrorCode());
        assertEquals("AVATAR_TYPE", noType.getErrorCode());
        verifyNoInteractions(r2);

        userService.uploadAvatar(u.getId(), new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1}));
        verify(r2).uploadBytes(eq("avatars/" + u.getId()), any(), eq("image/jpeg"));
    }

    @Test
    void lockingStaff_revokesOldTokens_soUnlockDoesNotReviveThem() {
        User staff = user("STAFF");
        when(repo.save(staff)).thenReturn(staff);

        adminService.setStatus(staff.getId(), status("LOCKED"));

        assertNotNull(staff.getTokensValidAfter());
    }
}
