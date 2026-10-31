package com.example.art_course.user;

import com.example.art_course.admin.dto.StaffStatusRequest;
import com.example.art_course.admin.service.AdminUserService;
import com.example.art_course.auth.repository.AuthRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.modules.moderation.service.ModerationService;
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
    private final com.example.art_course.modules.systemconfig.service.ConfigService config =
            mock(com.example.art_course.modules.systemconfig.service.ConfigService.class);
    private final UserService userService = new UserService(repo, authRepo, encoder, r2, config);
    private final ModerationService moderation = mock(ModerationService.class);
    private final AdminUserService adminService = new AdminUserService(repo, encoder, moderation);

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
    void decideTeacher_pending_changesStatusAndRecordsReview() {
        User u = user("TEACHER");
        UUID staff = UUID.randomUUID();
        when(repo.decidePendingTeacher(u.getId(), "REJECTED")).thenReturn(1);

        adminService.decideTeacher(u.getId(), staff, "REJECTED", "REJECTED", "Thiếu chứng chỉ");

        verify(moderation).recordModerationReview(staff, "TEACHER_PROFILE", u.getId(), "REJECTED", "Thiếu chứng chỉ");
    }

    @Test
    void decideTeacher_alreadyDecided_isRejectedAndNotLogged() {
        User u = user("TEACHER");
        when(repo.decidePendingTeacher(u.getId(), "ACTIVE")).thenReturn(0);
        when(repo.existsById(u.getId())).thenReturn(true);

        AppError e = assertThrows(AppError.class,
                () -> adminService.decideTeacher(u.getId(), UUID.randomUUID(), "ACTIVE", "APPROVED", null));

        assertEquals("NOT_PENDING_TEACHER", e.getErrorCode());
        verifyNoInteractions(moderation);
    }

    @Test
    void decideTeacher_unknownUser_is404() {
        UUID id = UUID.randomUUID();
        when(repo.decidePendingTeacher(id, "ACTIVE")).thenReturn(0);
        when(repo.existsById(id)).thenReturn(false);

        AppError e = assertThrows(AppError.class,
                () -> adminService.decideTeacher(id, UUID.randomUUID(), "ACTIVE", "APPROVED", null));

        assertEquals("USER_NOT_FOUND", e.getErrorCode());
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
    void changePassword_samePassword_isRejectedAfterVerifyingTheCurrentOne() {
        User u = user("PARENT");
        when(encoder.matches("old-pass", "hash")).thenReturn(true);

        AppError e = assertThrows(AppError.class, () -> userService.changePassword(u.getId(), pw("old-pass", "old-pass")));

        assertEquals("SAME_PASSWORD", e.getErrorCode());
        verify(repo, never()).save(any());
        assertEquals("hash", u.getPassword());
    }

    @Test
    void changePassword_childAccountIsManagedByTheParent() {
        User child = user("CHILD");
        when(encoder.matches("old", "hash")).thenReturn(true);

        AppError e = assertThrows(AppError.class, () -> userService.changePassword(child.getId(), pw("old", "newpassword1")));

        assertEquals("CHILD_PASSWORD_BY_PARENT", e.getErrorCode());
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
    void updateProfile_parentSavesShippingAddressInProfile() {
        User parent = user("PARENT");
        String address = "12 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh";
        when(authRepo.updateParentProfile(parent.getId(), null, address, null)).thenReturn(1);
        when(authRepo.findParentProfile(parent.getId())).thenReturn(Optional.of(
                new AuthRepository.ProfileRow("Mai", address, null, "0901234567", null, null)));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setShippingAddress(address);

        var response = userService.updateProfile(parent.getId(), request);

        verify(authRepo).updateParentProfile(parent.getId(), null, address, null);
        assertEquals(address, response.getShippingAddress());
        assertEquals("Mai", response.getFullName());
        assertEquals("0901234567", response.getPhone());
    }

    @Test
    void updateProfile_teacher_savesPhoneBioAndCertificatesAsLines() {
        User u = user("TEACHER");
        when(config.getDefaultShippingFee()).thenReturn(com.example.art_course.common.Money.of(30000));
        when(authRepo.updateTeacherProfile(u.getId(), null, null, "0901234567", "Dạy vẽ 8 năm",
                "Chứng chỉ A\nChứng chỉ B", null, false)).thenReturn(1);
        when(authRepo.findTeacherProfile(u.getId())).thenReturn(Optional.of(new AuthRepository.ProfileRow(
                "Lan", null, "VCB 1234", "0901234567", "Dạy vẽ 8 năm", "Chứng chỉ A\nChứng chỉ B")));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setPhone("0901234567");
        req.setBio("Dạy vẽ 8 năm");
        req.setCertificates(java.util.List.of(" Chứng chỉ A ", "", "Chứng chỉ B"));

        var res = userService.updateProfile(u.getId(), req);

        assertEquals("0901234567", res.getPhone());
        assertEquals(java.util.List.of("Chứng chỉ A", "Chứng chỉ B"), res.getCertificates());
    }

    @Test
    void updateProfile_teacher_emptyCertificatesClearsAndNullKeeps() {
        User u = user("TEACHER");
        when(config.getDefaultShippingFee()).thenReturn(com.example.art_course.common.Money.of(30000));
        when(authRepo.updateTeacherProfile(u.getId(), null, null, null, null, "", null, false)).thenReturn(1);
        when(authRepo.findTeacherProfile(u.getId())).thenReturn(Optional.of(
                new AuthRepository.ProfileRow("Lan", null, "", null, null, null)));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCertificates(java.util.List.of());

        var res = userService.updateProfile(u.getId(), req);

        assertEquals(java.util.List.of(), res.getCertificates());
    }

    @Test
    void updateProfile_parent_goesThroughRepository() {
        User u = user("PARENT");
        when(authRepo.updateParentProfile(u.getId(), "An", "HCM", null)).thenReturn(1);
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
    void updateProfile_parent_savesPhone() {
        User u = user("PARENT");
        when(authRepo.updateParentProfile(u.getId(), null, null, "0901234567")).thenReturn(1);
        when(authRepo.findParentProfile(u.getId())).thenReturn(Optional.of(
                new AuthRepository.ProfileRow("An", null, null, "0901234567", null, null)));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setPhone("0901234567");

        assertEquals("0901234567", userService.updateProfile(u.getId(), req).getPhone());
    }

    @Test
    void updateProfile_teacher_savesShippingFee() {
        User u = user("TEACHER");
        when(authRepo.updateTeacherProfile(u.getId(), null, null, null, null, null, 25000L, false)).thenReturn(1);
        when(authRepo.findTeacherProfile(u.getId())).thenReturn(Optional.of(
                new AuthRepository.ProfileRow("Lan", null, "", null, null, null, 25000L)));
        when(config.getDefaultShippingFee()).thenReturn(com.example.art_course.common.Money.of(30000));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setShippingFee(25000L);

        var res = userService.updateProfile(u.getId(), req);

        assertEquals(25000L, res.getShippingFee());
        // Giáo viên luôn thấy mức mặc định của sàn bên cạnh phí riêng của mình.
        assertEquals(30000L, res.getDefaultShippingFee());
    }

    @Test
    void updateProfile_teacher_canGoBackToTheDefaultShippingFee() {
        User u = user("TEACHER");
        when(authRepo.updateTeacherProfile(u.getId(), null, null, null, null, null, null, true)).thenReturn(1);
        when(authRepo.findTeacherProfile(u.getId())).thenReturn(Optional.of(
                new AuthRepository.ProfileRow("Lan", null, "", null, null, null, null)));
        when(config.getDefaultShippingFee()).thenReturn(com.example.art_course.common.Money.of(30000));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUseDefaultShippingFee(true);

        var res = userService.updateProfile(u.getId(), req);

        verify(authRepo).updateTeacherProfile(u.getId(), null, null, null, null, null, null, true);
        assertNull(res.getShippingFee());
        assertEquals(30000L, res.getDefaultShippingFee());
    }

    @Test
    void profile_defaultShippingFeeIsOnlyForTeachers() {
        User parent = user("PARENT");
        when(authRepo.findParentProfile(parent.getId())).thenReturn(Optional.of(
                new AuthRepository.ProfileRow("Mai", "", null)));

        assertNull(userService.getProfile(parent.getId()).getDefaultShippingFee());
        verifyNoInteractions(config);
    }

    @Test
    void certificateFile_uploadKeepsKeysAndStaffCanRead() {
        User teacher = user("TEACHER");
        User staff = user("STAFF");
        User stranger = user("TEACHER");
        when(authRepo.findTeacherProfile(teacher.getId())).thenReturn(Optional.of(
                new AuthRepository.ProfileRow("Lan", null, "", null, null, "Chứng chỉ A\nChứng chỉ B")));
        when(authRepo.findCertificateKeys(teacher.getId())).thenReturn("old-key\n");
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1, 2});

        userService.uploadCertificateFile(teacher.getId(), 1, pdf);

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(authRepo).updateCertificateKeys(eq(teacher.getId()), keys.capture());
        String[] saved = keys.getValue().split("\n");
        assertEquals("old-key", saved[0]);
        assertTrue(saved[1].startsWith("certificates/" + teacher.getId() + "/"));

        // sai định dạng / ngoài phạm vi bị từ chối
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1});
        assertEquals("FILE_TYPE", assertThrows(AppError.class,
                () -> userService.uploadCertificateFile(teacher.getId(), 0, gif)).getErrorCode());
        assertEquals("CERTIFICATE_NOT_FOUND", assertThrows(AppError.class,
                () -> userService.uploadCertificateFile(teacher.getId(), 5, pdf)).getErrorCode());

        // chủ, Staff được xem; giáo viên khác thì 403
        when(authRepo.findCertificateKeys(teacher.getId())).thenReturn("old-key\nk2");
        userService.certificateFile(teacher.getId(), teacher.getId(), 0);
        userService.certificateFile(staff.getId(), teacher.getId(), 1);
        verify(r2).getObject("old-key");
        assertEquals(403, assertThrows(AppError.class,
                () -> userService.certificateFile(stranger.getId(), teacher.getId(), 0)).getStatus().value());
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
