package com.example.art_course.messaging.repository;

import com.example.art_course.messaging.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository
        extends JpaRepository<Message, UUID> {

    List<Message> findAllByConversationIdOrderByCreatedAtAsc(
            UUID conversationId
    );

    List<Message> findAllBySenderId(UUID senderId);
}