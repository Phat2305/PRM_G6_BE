package com.example.art_course.messaging.service;

import com.example.art_course.messaging.dto.CreateMessageRequest;
import com.example.art_course.messaging.dto.MessageResponse;
import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.entity.Message;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.messaging.repository.MessageRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
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

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user.getId())
                .content(request.getContent().trim())
                .isOffer(Boolean.TRUE.equals(request.getIsOffer()))
                .build();

        return toResponse(messageRepository.save(message));
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

        User user = findUser(username);
        Conversation conversation = findConversation(conversationId);

        checkAccess(conversation, user);

        return messageRepository
                .findAllByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toResponse)
                .toList();
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

    private MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .isOffer(message.getIsOffer())
                .createdAt(message.getCreatedAt())
                .build();
    }
}