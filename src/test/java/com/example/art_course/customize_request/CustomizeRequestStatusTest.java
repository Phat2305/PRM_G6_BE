package com.example.art_course.customize_request;

import com.example.art_course.customize_request.dto.CreateCustomizeRequestRequest;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.customize_request.service.CustomizeRequestService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Trạng thái yêu cầu theo SRS (OPEN/ACCEPTED...); "pending" của app cũ vẫn được hiểu là OPEN.
class CustomizeRequestStatusTest {

    private final CustomizeRequestRepository repo = mock(CustomizeRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CustomizeRequestService service = new CustomizeRequestService(repo, users);

    private String login(String role) {
        User u = new User(UUID.randomUUID(), "u", "hash", role, "ACTIVE", null, null);
        when(users.findByPrincipal(u.getId().toString())).thenReturn(Optional.of(u));
        return u.getId().toString();
    }

    @Test
    void newRequestIsOpen() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        CreateCustomizeRequestRequest req = new CreateCustomizeRequestRequest();
        req.setTitle("Vẽ phong cảnh");

        assertEquals("OPEN", service.create(req, login("PARENT")).getStatus());
    }

    @Test
    void pendingAndLowercaseQueryOpenAndAccepted() {
        String teacher = login("TEACHER");
        when(repo.findAllByStatus(any())).thenReturn(List.of());

        service.getByStatus("pending", teacher);
        service.getByStatus("open", teacher);
        service.getByStatus("accepted", teacher);

        verify(repo, times(2)).findAllByStatus("OPEN");
        verify(repo).findAllByStatus("ACCEPTED");
        assertThrows(ResponseStatusException.class, () -> service.getByStatus("DONE", teacher));
    }

    @Test
    void onlyOpenRequestCanBeDeleted() {
        String parent = login("PARENT");
        UUID parentId = UUID.fromString(parent);
        UUID id = UUID.randomUUID();
        CustomizeRequest accepted = CustomizeRequest.builder().id(id).parentId(parentId).status("ACCEPTED").build();
        when(repo.findById(id)).thenReturn(Optional.of(accepted));

        assertThrows(ResponseStatusException.class, () -> service.delete(id, parent));

        accepted.setStatus("OPEN");
        service.delete(id, parent);
        verify(repo).delete(accepted);
    }
}
