package com.example.art_course.messaging.repository;

import com.example.art_course.messaging.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository
        extends JpaRepository<Conversation, UUID> {

    List<Conversation> findAllByParentId(UUID parentId);

    List<Conversation> findAllByTeacherId(UUID teacherId);

    List<Conversation> findAllByParentIdAndTeacherId(
            UUID parentId,
            UUID teacherId
    );

    Optional<Conversation> findFirstByContextTypeAndContextIdAndTeacherId(
            String contextType,
            UUID contextId,
            UUID teacherId
    );

    List<Conversation> findAllByContextTypeAndContextId(
            String contextType,
            UUID contextId
    );
}