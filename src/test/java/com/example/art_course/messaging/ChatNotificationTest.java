package com.example.art_course.messaging;

import com.example.art_course.messaging.dto.CreateMessageRequest;
import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.messaging.repository.MessageRepository;
import com.example.art_course.messaging.service.MessageService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.notification.entity.Notification;
import com.example.art_course.modules.notification.repository.NotificationRepository;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// GĐ C (docs/chat-offer-plan.md): thông báo tin nhắn/báo giá và hỏi tin mới bằng ?after=.
class ChatNotificationTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService notificationService = new NotificationService(notifications);

    private final MessageRepository messages = mock(MessageRepository.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Conversation conversation = Conversation.builder()
            .id(UUID.randomUUID()).parentId(UUID.randomUUID()).teacherId(UUID.randomUUID()).build();

    @Test
    void newMessageNotification_isCoalescedWhileUnread() {
        UUID recipient = UUID.randomUUID();
        when(notifications.existsByRecipientIdAndTypeAndReferenceIdAndIsReadFalse(
                recipient, "NEW_MESSAGE", conversation.getId())).thenReturn(true);
        notificationService.notifyNewMessage(recipient, conversation.getId(), "Chào cô");
        verify(notifications, never()).save(any());

        when(notifications.existsByRecipientIdAndTypeAndReferenceIdAndIsReadFalse(
                recipient, "NEW_MESSAGE", conversation.getId())).thenReturn(false);
        notificationService.notifyNewMessage(recipient, conversation.getId(), "Chào cô");
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertEquals(recipient, saved.getValue().getRecipientId());
        assertEquals(conversation.getId(), saved.getValue().getReferenceId());
    }

    @Test
    void offerNotifications_formatPriceInVnd() {
        UUID parent = UUID.randomUUID();
        notificationService.notifyNewOffer(UUID.randomUUID(), parent, new BigDecimal("350000"), 7);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertEquals("NEW_OFFER", saved.getValue().getType());
        assertEquals("Giáo viên gửi báo giá 350.000đ, bàn giao trong 7 ngày.", saved.getValue().getContent());
    }

    @Test
    void parentMessage_notifiesTeacher_andAfterReturnsOnlyNewer() {
        UUID parentId = conversation.getParentId();
        when(users.findByPrincipal(parentId.toString()))
                .thenReturn(Optional.of(new User(parentId, "p", "h", "PARENT", "ACTIVE", null, null)));
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
        NotificationService notify = mock(NotificationService.class);
        MessageService service = new MessageService(messages, conversations, users, mock(OfferRepository.class));
        ReflectionTestUtils.setField(service, "notificationService", notify);

        CreateMessageRequest request = new CreateMessageRequest();
        request.setConversationId(conversation.getId());
        request.setContent("Bé 7 tuổi ạ");
        service.create(request, parentId.toString());
        verify(notify).notifyNewMessage(eq(conversation.getTeacherId()), eq(conversation.getId()), eq("Bé 7 tuổi ạ"));

        OffsetDateTime after = OffsetDateTime.now().minusMinutes(1);
        when(messages.findAllByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(conversation.getId(), after))
                .thenReturn(List.of());
        service.getByConversationId(conversation.getId(), after, parentId.toString());
        verify(messages).findAllByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(conversation.getId(), after);
        verify(messages, never()).findAllByConversationIdOrderByCreatedAtAsc(any());
    }
}
