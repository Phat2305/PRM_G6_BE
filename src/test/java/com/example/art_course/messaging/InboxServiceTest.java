package com.example.art_course.messaging;

import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.messaging.repository.ConversationSummaryRepository;
import com.example.art_course.messaging.service.InboxService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// GĐ B: mỗi bên đánh dấu đã đọc mốc của riêng mình; người ngoài hội thoại bị chặn.
class InboxServiceTest {

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final InboxService service =
            new InboxService(conversations, mock(ConversationSummaryRepository.class), users);

    private final Conversation conversation = Conversation.builder()
            .id(UUID.randomUUID()).parentId(UUID.randomUUID()).teacherId(UUID.randomUUID()).build();

    private String login(UUID id) {
        when(users.findByPrincipal(id.toString()))
                .thenReturn(Optional.of(new User(id, "u", "h", "PARENT", "ACTIVE", null, null)));
        return id.toString();
    }

    @Test
    void markRead_setsOnlyCallersMarker_andRejectsOutsiders() {
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        service.markRead(conversation.getId(), login(conversation.getParentId()));
        assertNotNull(conversation.getParentLastReadAt());
        assertNull(conversation.getTeacherLastReadAt());

        service.markRead(conversation.getId(), login(conversation.getTeacherId()));
        assertNotNull(conversation.getTeacherLastReadAt());

        assertThrows(ResponseStatusException.class,
                () -> service.markRead(conversation.getId(), login(UUID.randomUUID())));
        verify(conversations, times(2)).save(conversation);
    }
}
