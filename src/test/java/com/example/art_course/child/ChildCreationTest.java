package com.example.art_course.child;

import com.example.art_course.child.dto.CreateChildRequest;
import com.example.art_course.child.dto.UpdateChildRequest;
import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.child.service.ChildService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChildCreationTest {

    @Mock
    private ChildRepository childRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ChildService childService;

    private UUID parentId;
    private User parentUser;

    @BeforeEach
    void setUp() {
        parentId = UUID.randomUUID();
        parentUser = new User();
        parentUser.setId(parentId);
        parentUser.setUsername("parent@example.com");
        parentUser.setRole("PARENT");
        parentUser.setStatus("ACTIVE");

        when(userRepository.findById(parentId)).thenReturn(Optional.of(parentUser));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        when(childRepository.save(any(Child.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Tạo tài khoản trẻ em thành công với đầy đủ thông tin chuẩn (BE1 format)")
    void testCreateChildFull() {
        CreateChildRequest request = new CreateChildRequest();
        request.setUsername("be_bo_01");
        request.setPassword("Password123!");
        request.setDisplayName("Bé Bo");
        request.setBirthYear((short) 2018);

        when(userRepository.existsByUsername("be_bo_01")).thenReturn(false);

        Child child = childService.createChild(parentId, request);

        assertNotNull(child);
        assertEquals("Bé Bo", child.getDisplayName());
        assertEquals((short) 2018, child.getBirthYear());
        assertEquals(parentId, child.getParentId());
        verify(userRepository, times(1)).save(argThat(u -> "be_bo_01".equals(u.getUsername()) && "CHILD".equals(u.getRole())));
    }

    @Test
    @DisplayName("Tạo tài khoản trẻ em thành công với format Swagger/Frontend (chỉ gửi name và birthDate)")
    void testCreateChildSwaggerFormat() {
        CreateChildRequest request = new CreateChildRequest();
        request.setDisplayName("Bé An");
        request.setBirthDate("2019-06-15");

        Child child = childService.createChild(parentId, request);

        assertNotNull(child);
        assertEquals("Bé An", child.getDisplayName());
        assertEquals((short) 2019, child.getBirthYear());
        assertEquals(parentId, child.getParentId());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertNotNull(createdUser.getUsername());
        assertTrue(createdUser.getUsername().startsWith("child_"));
        assertEquals("CHILD", createdUser.getRole());
    }

    @Test
    @DisplayName("Tạo tài khoản trẻ em với mật khẩu ngắn dạng mã PIN (ví dụ 123456)")
    void testCreateChildShortPassword() {
        CreateChildRequest request = new CreateChildRequest();
        request.setUsername("be_na");
        request.setPassword("123456");
        request.setDisplayName("Bé Na");
        request.setBirthYear((short) 2020);

        when(userRepository.existsByUsername("be_na")).thenReturn(false);

        Child child = childService.createChild(parentId, request);

        assertNotNull(child);
        assertEquals("Bé Na", child.getDisplayName());
        verify(passwordEncoder).encode("123456");
    }

    @Test
    @DisplayName("Cập nhật hồ sơ trẻ em hỗ trợ cả birthDate")
    void testUpdateChildBirthDate() {
        UUID childUserId = UUID.randomUUID();
        Child existingChild = Child.builder()
                .userId(childUserId)
                .parentId(parentId)
                .displayName("Bé Cũ")
                .birthYear((short) 2017)
                .build();

        when(childRepository.findByUserIdAndParentId(childUserId, parentId)).thenReturn(Optional.of(existingChild));

        UpdateChildRequest updateReq = new UpdateChildRequest();
        updateReq.setDisplayName("Bé Mới");
        updateReq.setBirthDate("2021-12-01");

        Child updated = childService.updateChild(parentId, childUserId, updateReq);

        assertEquals("Bé Mới", updated.getDisplayName());
        assertEquals((short) 2021, updated.getBirthYear());
    }
}
