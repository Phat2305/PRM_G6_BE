package com.example.art_course.messaging.service;

import com.example.art_course.messaging.dto.CreateMessageRequest;
import com.example.art_course.messaging.dto.MessageResponse;
import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.entity.Message;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.messaging.repository.MessageRepository;
import com.example.art_course.offer.entity.Offer;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final OfferRepository offerRepository;

    // Inject qua field để test dựng service bằng constructor không phải đổi; null thì không gửi thông báo.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.art_course.modules.notification.NotificationService notificationService;

    // MSG-05: giới hạn độ dài một tin nhắn
    static final int MAX_CONTENT = 2000;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            OfferRepository offerRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
    }

    @Transactional
    public MessageResponse create(
            CreateMessageRequest request,
            String username) {

        User user = findUser(username);

        Conversation conversation = findConversation(
                request.getConversationId());

        checkAccess(conversation, user);

        if (request.getContent() == null
                || request.getContent().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message content cannot be empty");
        }

        if (request.getContent().trim().length() > MAX_CONTENT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message is too long (max " + MAX_CONTENT + " characters)");
        }

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user.getId())
                .content(request.getContent().trim())
                // Tin báo giá chỉ tạo qua POST /api/offers (có offer_id); bỏ qua cờ isOffer từ client.
                .isOffer(false)
                .build();

        MessageResponse saved = toResponse(messageRepository.save(message));
        if (notificationService != null) {
            UUID recipient = user.getId().equals(conversation.getParentId())
                    ? conversation.getTeacherId()
                    : conversation.getParentId();
            String text = message.getContent();
            notificationService.notifyNewMessage(recipient, conversation.getId(),
                    text.length() > 100 ? text.substring(0, 100) + "…" : text);
        }
        return saved;
    }

    public MessageResponse getById(UUID id, String username) {
        Message message = findById(id);
        User user = findUser(username);

        Conversation conversation =
                findConversation(message.getConversationId());

        checkAccess(conversation, user);

        return toResponse(message);
    }

    public List<MessageResponse> getByConversationId(
            UUID conversationId, String username) {
        return getByConversationId(conversationId, null, username);
    }

    public List<MessageResponse> getByConversationId(
            UUID conversationId, java.time.OffsetDateTime after, String username) {

        User user = findUser(username);
        Conversation conversation = findConversation(conversationId);

        checkAccess(conversation, user);

        return toResponses(after == null
                ? messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId)
                : messageRepository.findAllByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(
                        conversationId, after));
    }

    public List<MessageResponse> getBySenderId(
            UUID senderId, String username) {

        User user = findUser(username);

        if (!user.getId().equals(senderId) && !isAdmin(user)) {
            throw forbidden();
        }

        return messageRepository.findAllBySenderId(senderId)
                .stream()
                .filter(message -> {
                    Conversation conversation = findConversation(
                            message.getConversationId());
                    return hasAccess(conversation, user);
                })
                .map(this::toResponse)
                .toList();
    }

    private User findUser(String username) {
        return userRepository.findByPrincipal(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Conversation findConversation(UUID id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private Message findById(UUID id) {
        return messageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Message not found"));
    }

    private void checkAccess(Conversation conversation, User user) {
        if (!hasAccess(conversation, user)) {
            throw forbidden();
        }
    }

    private boolean hasAccess(Conversation conversation, User user) {
        return isAdmin(user)
                || user.getId().equals(conversation.getParentId())
                || user.getId().equals(conversation.getTeacherId());
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You cannot access this conversation");
    }

    // Gắn trạng thái/giá/số ngày của báo giá vào tin báo giá; đọc mọi báo giá một lần, không N+1.
    private List<MessageResponse> toResponses(List<Message> messages) {
        Map<UUID, Offer> offers = offerRepository.findAllById(messages.stream()
                        .map(Message::getOfferId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Offer::getId, o -> o));
        return messages.stream().map(m -> {
            MessageResponse response = toResponse(m);
            Offer offer = m.getOfferId() == null ? null : offers.get(m.getOfferId());
            if (offer != null) {
                response.setOfferStatus(offer.getStatus());
                response.setOfferPrice(offer.getPrice());
                response.setDeliveryDays(offer.getDeliveryDays());
            }
            return response;
        }).toList();
    }

    private MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .offerId(message.getOfferId())
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .isOffer(message.getIsOffer())
                .createdAt(message.getCreatedAt())
                .build();
    }
}