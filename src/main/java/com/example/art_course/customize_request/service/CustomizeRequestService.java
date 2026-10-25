
package com.example.art_course.customize_request.service;

import com.example.art_course.customize_request.dto.CreateCustomizeRequestRequest;
import com.example.art_course.customize_request.dto.CustomizeRequestResponse;
import com.example.art_course.customize_request.dto.UpdateCustomizeRequestRequest;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomizeRequestService {

    // Tên trạng thái theo SRS; dữ liệu trong DB cũng lưu dạng này.
    public static final String OPEN = "OPEN";
    private static final Set<String> ALLOWED_STATUSES =
            Set.of(OPEN, "ACCEPTED", "REJECTED", "CANCELLED");

    private final CustomizeRequestRepository repository;
    private final UserRepository userRepository;

    public CustomizeRequestService(
            CustomizeRequestRepository repository,
            UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CustomizeRequestResponse create(
            CreateCustomizeRequestRequest request,
            String username) {

        if (request.getTitle() == null
                || request.getTitle().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request title is required");
        }

        User user = getUser(username);
        requireRole(user, "PARENT");

        CustomizeRequest entity = CustomizeRequest.builder()
                .parentId(user.getId())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .category(request.getCategory())
                .childAge(request.getChildAge())
                .budget(request.getBudget())
                .deadline(request.getDeadline())
                .status(OPEN)
                .build();

        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public CustomizeRequestResponse getById(
            UUID id, String username) {

        User user = getUser(username);
        CustomizeRequest entity = findById(id);

        if (!isOwner(entity, user)
                && !canManageRequests(user)) {
            throw forbidden();
        }

        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CustomizeRequestResponse> getByParentId(
            UUID parentId, String username) {

        User user = getUser(username);

        if (!user.getId().equals(parentId)
                && !canManageRequests(user)) {
            throw forbidden();
        }

        return repository.findAllByParentId(parentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomizeRequestResponse> getByStatus(
            String status, String username) {

        User user = getUser(username);
        requireRequestManager(user);

        String normalized = normalizeStatus(status);

        return repository.findAllByStatus(normalized)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomizeRequestResponse> getByCategory(
            String category, String username) {

        User user = getUser(username);
        requireRequestManager(user);

        if (category == null || category.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category is required");
        }

        return repository.findAllByCategoryIgnoreCase(category.trim())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CustomizeRequestResponse update(
            UUID id,
            UpdateCustomizeRequestRequest request,
            String username) {

        User user = getUser(username);
        CustomizeRequest entity = findById(id);

        requireOwner(entity, user);
        requirePending(entity);

        if (request.getTitle() == null
                || request.getTitle().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request title is required");
        }

        entity.setTitle(request.getTitle().trim());
        entity.setDescription(request.getDescription());
        entity.setCategory(request.getCategory());
        entity.setChildAge(request.getChildAge());
        entity.setBudget(request.getBudget());
        entity.setDeadline(request.getDeadline());

        return toResponse(repository.save(entity));
    }

    @Transactional
    public CustomizeRequestResponse updateStatus(
            UUID id, String status, String username) {

        User user = getUser(username);
        requireRequestManager(user);

        String normalized = normalizeStatus(status);
        CustomizeRequest entity = findById(id);

        entity.setStatus(normalized);

        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id, String username) {
        User user = getUser(username);
        CustomizeRequest entity = findById(id);

        requireOwner(entity, user);
        requirePending(entity);

        repository.delete(entity);
    }

    private User getUser(String username) {
        return userRepository.findByPrincipal(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));
    }

    private CustomizeRequest findById(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request ID is required");
        }

        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customize request not found"));
    }

    private boolean isOwner(
            CustomizeRequest entity, User user) {
        return entity.getParentId() != null
                && entity.getParentId().equals(user.getId());
    }

    private void requireOwner(
            CustomizeRequest entity, User user) {
        if (!isOwner(entity, user)) {
            throw forbidden();
        }
    }

    private void requirePending(CustomizeRequest entity) {
        if (!OPEN.equalsIgnoreCase(entity.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only open requests can be edited or deleted");
        }
    }

    private boolean canManageRequests(User user) {
        return hasRole(user, "TEACHER")
                || hasRole(user, "ADMIN");
    }

    private void requireRequestManager(User user) {
        if (!canManageRequests(user)) {
            throw forbidden();
        }
    }

    private void requireRole(User user, String role) {
        if (!hasRole(user, role)) {
            throw forbidden();
        }
    }

    private boolean hasRole(User user, String role) {
        return user.getRole() != null
                && user.getRole().trim().equalsIgnoreCase(role);
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status is required");
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        // App cũ vẫn gửi "pending"
        if ("PENDING".equals(normalized)) {
            normalized = OPEN;
        }

        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid request status");
        }

        return normalized;
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not allowed to access this request");
    }

    private CustomizeRequestResponse toResponse(
            CustomizeRequest entity) {

        return CustomizeRequestResponse.builder()
                .id(entity.getId())
                .parentId(entity.getParentId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .childAge(entity.getChildAge())
                .budget(entity.getBudget())
                .deadline(entity.getDeadline())
                .status(entity.getStatus())
                .build();
    }
}
