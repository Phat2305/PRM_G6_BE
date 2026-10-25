package com.example.art_course.messaging.service;

import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.customize_request.service.CustomizeRequestService;
import com.example.art_course.messaging.dto.ConversationResponse;
import com.example.art_course.messaging.dto.CreateConversationRequest;
import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository repository;
    private final UserRepository userRepository;
    private final CustomizeRequestRepository requestRepository;

    static final String CUSTOMIZE_CONTEXT = "CUSTOMIZE_REQUEST";

    public ConversationService(
            ConversationRepository repository,
            UserRepository userRepository,
            CustomizeRequestRepository requestRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional
    public ConversationResponse create(
            CreateConversationRequest request,
            String username) {

        User user = findUser(username);

        UUID userId = user.getId();
        String role = user.getRole();

        if (request.getParentId() == null && "PARENT".equalsIgnoreCase(role)) {
            request.setParentId(userId);
        }
        if (request.getTeacherId() == null && "TEACHER".equalsIgnoreCase(role)) {
            request.setTeacherId(userId);
        }

        boolean isParent = "PARENT".equalsIgnoreCase(role)
                && userId.equals(request.getParentId());

        boolean isTeacher = "TEACHER".equalsIgnoreCase(role)
                && userId.equals(request.getTeacherId());

        if (!isParent && !isTeacher
                && !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot create this conversation");
        }

        if (request.getParentId() == null
                || request.getTeacherId() == null
                || request.getContextType() == null
                || request.getContextType().isBlank()
                || request.getContextId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing required conversation information");
        }

        String contextType = request.getContextType().trim();

        // CUS-04: mỗi giáo viên một hội thoại cho một ngữ cảnh; gọi lại thì trả hội thoại cũ.
        var existing = repository.findFirstByContextTypeAndContextIdAndTeacherId(
                contextType, request.getContextId(), request.getTeacherId());
        if (existing.isPresent()) {
            checkAccess(existing.get(), user);
            return toResponse(existing.get());
        }

        if (CUSTOMIZE_CONTEXT.equalsIgnoreCase(contextType)) {
            CustomizeRequest customize = requestRepository.findById(request.getContextId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Customize request not found"));
            if (!customize.getParentId().equals(request.getParentId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Parent does not own this request");
            }
            if (!CustomizeRequestService.OPEN.equalsIgnoreCase(customize.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This request is no longer open");
            }
        }

        Conversation conversation = Conversation.builder()
                .parentId(request.getParentId())
                .teacherId(request.getTeacherId())
                .lessonId(request.getLessonId())
                .contextType(contextType)
                .contextId(request.getContextId())
                .build();

        return toResponse(repository.save(conversation));
    }

    public ConversationResponse getById(UUID id, String username) {
        Conversation conversation = findById(id);
        checkAccess(conversation, findUser(username));
        return toResponse(conversation);
    }

    public List<ConversationResponse> getByParentId(
            UUID parentId, String username) {

        User user = findUser(username);

        if (!isAdmin(user)
                && !user.getId().equals(parentId)) {
            throw forbidden();
        }

        return repository.findAllByParentId(parentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ConversationResponse> getByTeacherId(
            UUID teacherId, String username) {

        User user = findUser(username);

        if (!isAdmin(user)
                && !user.getId().equals(teacherId)) {
            throw forbidden();
        }

        return repository.findAllByTeacherId(teacherId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ConversationResponse> getByContext(
            String contextType,
            UUID contextId,
            String username) {

        User user = findUser(username);

        List<Conversation> conversations =
                repository.findAllByContextTypeAndContextId(
                        contextType.trim(), contextId);

        return conversations.stream()
                .filter(conversation -> hasAccess(conversation, user))
                .map(this::toResponse)
                .toList();
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

    private User findUser(String username) {
        return userRepository.findByPrincipal(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Conversation findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN, "You cannot access this conversation");
    }

    private ConversationResponse toResponse(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .parentId(conversation.getParentId())
                .teacherId(conversation.getTeacherId())
                .lessonId(conversation.getLessonId())
                .contextType(conversation.getContextType())
                .contextId(conversation.getContextId())
                .build();
    }
}