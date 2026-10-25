package com.example.art_course.messaging;

import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.messaging.dto.CreateConversationRequest;
import com.example.art_course.messaging.dto.CreateMessageRequest;
import com.example.art_course.messaging.dto.MessageResponse;
import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.entity.Message;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.messaging.repository.MessageRepository;
import com.example.art_course.messaging.service.ConversationService;
import com.example.art_course.messaging.service.MessageService;
import com.example.art_course.offer.dto.CreateOfferRequest;
import com.example.art_course.offer.dto.OfferResponse;
import com.example.art_course.offer.entity.Offer;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.offer.service.OfferService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// GĐ A (docs/chat-offer-plan.md): báo giá gắn với tin nhắn, thay báo giá cũ, hội thoại không trùng.
class ChatOfferTest {

    private final OfferRepository offers = mock(OfferRepository.class);
    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);

    private final UUID teacherId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private final CustomizeRequest request = CustomizeRequest.builder()
            .id(UUID.randomUUID()).parentId(parentId).title("Vẽ mèo").status("OPEN").build();
    private final Conversation conversation = Conversation.builder()
            .id(UUID.randomUUID()).parentId(parentId).teacherId(teacherId)
            .contextType("CUSTOMIZE_REQUEST").contextId(request.getId()).build();

    private String login(UUID id, String role) {
        when(users.findByPrincipal(id.toString()))
                .thenReturn(Optional.of(new User(id, "u", "h", role, "ACTIVE", null, null)));
        return id.toString();
    }

    private CreateOfferRequest offerRequest(UUID conversationId, Integer days) {
        CreateOfferRequest r = new CreateOfferRequest();
        r.setRequestId(request.getId());
        r.setPrice(new BigDecimal("350000"));
        r.setMessage("Lộ trình 4 buổi");
        r.setConversationId(conversationId);
        r.setDeliveryDays(days);
        return r;
    }

    private OfferService offerService() {
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(offers.save(any())).thenAnswer(i -> {
            Offer o = i.getArgument(0);
            if (o.getId() == null) o.setId(UUID.randomUUID());
            return o;
        });
        when(messages.save(any())).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        return new OfferService(offers, requests, users, conversations, messages);
    }

    @Test
    void newOffer_supersedesOwnPendingOffer_andCreatesLinkedMessage() {
        String teacher = login(teacherId, "TEACHER");
        Offer old = Offer.builder().id(UUID.randomUUID()).requestId(request.getId())
                .teacherId(teacherId).price(BigDecimal.ONE).status("pending").build();
        when(offers.findAllByRequestIdAndTeacherId(request.getId(), teacherId)).thenReturn(List.of(old));
        OfferService service = offerService();

        OfferResponse created = service.create(offerRequest(conversation.getId(), 7), teacher);

        assertEquals("superseded", old.getStatus());
        assertEquals(7, created.getDeliveryDays());
        assertNotNull(created.getMessageId());
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(messages).save(sent.capture());
        assertTrue(sent.getValue().getIsOffer());
        assertEquals(created.getId(), sent.getValue().getOfferId());
        assertEquals(conversation.getId(), sent.getValue().getConversationId());
    }

    @Test
    void offer_rejectsOtherTeachersConversation_andBadDeliveryDays() {
        String other = login(UUID.randomUUID(), "TEACHER");
        OfferService service = offerService();

        assertThrows(ResponseStatusException.class,
                () -> service.create(offerRequest(conversation.getId(), 7), other));

        String teacher = login(teacherId, "TEACHER");
        assertThrows(ResponseStatusException.class,
                () -> service.create(offerRequest(conversation.getId(), 61), teacher));
        verify(offers, never()).save(any());
        verify(messages, never()).save(any());
    }

    @Test
    void messages_carryCurrentOfferStatus_andClientCannotFakeOffers() {
        String parent = login(parentId, "PARENT");
        Offer offer = Offer.builder().id(UUID.randomUUID()).price(new BigDecimal("350000"))
                .deliveryDays(7).status("accepted").build();
        Message offerMsg = Message.builder().id(UUID.randomUUID()).conversationId(conversation.getId())
                .senderId(teacherId).content("Báo giá").isOffer(true).offerId(offer.getId()).build();
        Message plain = Message.builder().id(UUID.randomUUID()).conversationId(conversation.getId())
                .senderId(parentId).content("Chào cô").isOffer(false).build();
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(messages.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId()))
                .thenReturn(List.of(offerMsg, plain));
        when(offers.findAllById(any())).thenReturn(List.of(offer));
        MessageService service = new MessageService(messages, conversations, users, offers);

        List<MessageResponse> list = service.getByConversationId(conversation.getId(), parent);

        assertEquals("accepted", list.get(0).getOfferStatus());
        assertEquals(7, list.get(0).getDeliveryDays());
        assertNull(list.get(1).getOfferStatus());

        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
        CreateMessageRequest fake = new CreateMessageRequest();
        fake.setConversationId(conversation.getId());
        fake.setContent("Giảm còn 1đ");
        fake.setIsOffer(true);
        assertFalse(service.create(fake, parent).getIsOffer());

        fake.setContent("x".repeat(2001));
        assertThrows(ResponseStatusException.class, () -> service.create(fake, parent));
    }

    @Test
    void conversation_reusesExisting_andChecksRequestOwner() {
        String teacher = login(teacherId, "TEACHER");
        ConversationService service = new ConversationService(conversations, users, requests);
        CreateConversationRequest create = new CreateConversationRequest();
        create.setParentId(parentId);
        create.setTeacherId(teacherId);
        create.setContextType("CUSTOMIZE_REQUEST");
        create.setContextId(request.getId());

        when(conversations.findFirstByContextTypeAndContextIdAndTeacherId(
                "CUSTOMIZE_REQUEST", request.getId(), teacherId)).thenReturn(Optional.of(conversation));
        assertEquals(conversation.getId(), service.create(create, teacher).getId());
        verify(conversations, never()).save(any());

        when(conversations.findFirstByContextTypeAndContextIdAndTeacherId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        create.setParentId(UUID.randomUUID());
        assertThrows(ResponseStatusException.class, () -> service.create(create, teacher));

        create.setParentId(parentId);
        request.setStatus("FUNDED");
        assertThrows(ResponseStatusException.class, () -> service.create(create, teacher));
        verify(conversations, never()).save(any());
    }
}
