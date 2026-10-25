package com.example.art_course.messaging.service;

import com.example.art_course.messaging.dto.ConversationSummary;
import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.messaging.repository.ConversationSummaryRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboxService {

    private final ConversationRepository conversationRepository;
    private final ConversationSummaryRepository summaryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ConversationSummary> mine(String principal) {
        return summaryRepository.forUser(user(principal).getId());
    }

    // Chỉ hai bên của hội thoại mới đánh dấu được; mỗi bên có mốc riêng.
    @Transactional
    public void markRead(UUID conversationId, String principal) {
        User user = user(principal);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        OffsetDateTime now = OffsetDateTime.now();
        if (user.getId().equals(conversation.getParentId())) {
            conversation.setParentLastReadAt(now);
        } else if (user.getId().equals(conversation.getTeacherId())) {
            conversation.setTeacherLastReadAt(now);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this conversation");
        }
        conversationRepository.save(conversation);
    }

    private User user(String principal) {
        return userRepository.findByPrincipal(principal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
