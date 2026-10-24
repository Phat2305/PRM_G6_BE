package com.example.art_course.child;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.child.service.ChildService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Parent quản lý con: đặt lại mật khẩu, khóa/mở khóa")
class ChildManageTest {

    private final ChildRepository childRepo = mock(ChildRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ChildService service = new ChildService(childRepo, userRepo, encoder);
    private final UUID parentId = UUID.randomUUID();
    private final UUID childId = UUID.randomUUID();
    private User childUser;

    @BeforeEach
    void setUp() {
        childUser = new User();
        childUser.setId(childId);
        childUser.setUsername("bena");
        childUser.setPassword("old");
        childUser.setStatus("ACTIVE");
        Child child = Child.builder().userId(childId).parentId(parentId).displayName("Bé Na").build();
        when(childRepo.findByUserIdAndParentId(childId, parentId)).thenReturn(Optional.of(child));
        when(userRepo.findById(childId)).thenReturn(Optional.of(childUser));
        when(encoder.encode("matkhau123")).thenReturn("hashed");
    }

    @Test
    @DisplayName("đặt lại mật khẩu: lưu bản băm và thu hồi token cũ của con")
    void resetPassword() {
        service.resetPassword(parentId, childId, "matkhau123");
        assertEquals("hashed", childUser.getPassword());
        assertNotNull(childUser.getTokensValidAfter());
    }

    @Test
    @DisplayName("parent khác không đụng được vào con của người khác")
    void otherParentCannotManage() {
        UUID stranger = UUID.randomUUID();
        when(childRepo.findByUserIdAndParentId(childId, stranger)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.resetPassword(stranger, childId, "matkhau123"));
        assertThrows(ResponseStatusException.class, () -> service.setLocked(stranger, childId, true));
    }

    @Test
    @DisplayName("khóa rồi mở khóa: ACTIVE <-> LOCKED")
    void lockAndUnlock() {
        service.setLocked(parentId, childId, true);
        assertEquals("LOCKED", childUser.getStatus());
        service.setLocked(parentId, childId, true); // lặp lại không lỗi
        service.setLocked(parentId, childId, false);
        assertEquals("ACTIVE", childUser.getStatus());
    }

    @Test
    @DisplayName("con bị admin đình chỉ thì parent không mở khóa được")
    void adminSuspendedStaysSuspended() {
        childUser.setStatus("SUSPENDED");
        assertThrows(ResponseStatusException.class, () -> service.setLocked(parentId, childId, false));
        assertThrows(ResponseStatusException.class, () -> service.setLocked(parentId, childId, true));
        assertEquals("SUSPENDED", childUser.getStatus());
    }
}
