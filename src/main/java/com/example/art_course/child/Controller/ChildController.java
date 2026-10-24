
package com.example.art_course.child.Controller;

import com.example.art_course.child.dto.ChildResponse;
import com.example.art_course.child.dto.ChildLockRequest;
import com.example.art_course.child.dto.CreateChildRequest;
import com.example.art_course.child.dto.ResetChildPasswordRequest;
import com.example.art_course.child.dto.UpdateChildRequest;
import com.example.art_course.child.entity.Child;
import com.example.art_course.child.service.ChildService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/children")
public class ChildController {

    private final ChildService childService;
    private final UserRepository userRepository;

    public ChildController(
            ChildService childService,
            UserRepository userRepository
    ) {
        this.childService = childService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChildResponse createChild(
            @Valid @RequestBody CreateChildRequest request,
            Authentication authentication
    ) {
        UUID parentId = getCurrentUserId(authentication);

        return toResponse(
                childService.createChild(parentId, request)
        );
    }

    @GetMapping
    public List<ChildResponse> getChildren(
            Authentication authentication
    ) {
        UUID parentId = getCurrentUserId(authentication);

        return childService.getChildren(parentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{userId}")
    public ChildResponse getChild(
            @PathVariable UUID userId,
            Authentication authentication
    ) {
        UUID parentId = getCurrentUserId(authentication);

        return toResponse(
                childService.getChild(parentId, userId)
        );
    }

    @PutMapping("/{userId}")
    public ChildResponse updateChild(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateChildRequest request,
            Authentication authentication
    ) {
        UUID parentId = getCurrentUserId(authentication);

        return toResponse(
                childService.updateChild(
                        parentId, userId, request
                )
        );
    }

    // Parent sets a new password for the child; the child's old sessions stop working.
    @PutMapping("/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetChildPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ResetChildPasswordRequest request,
            Authentication authentication
    ) {
        UUID parentId = getCurrentUserId(authentication);

        childService.resetPassword(parentId, userId, request.getNewPassword());
    }

    @PutMapping("/{userId}/lock")
    public ChildResponse setChildLocked(
            @PathVariable UUID userId,
            @Valid @RequestBody ChildLockRequest request,
            Authentication authentication
    ) {
        UUID parentId = getCurrentUserId(authentication);

        return toResponse(
                childService.setLocked(parentId, userId, request.getLocked())
        );
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChild(
            @PathVariable UUID userId,
            Authentication authentication
    ) {
        UUID parentId = getCurrentUserId(authentication);

        childService.deleteChild(parentId, userId);
    }

    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new org.springframework.web.server
                    .ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        String principalName = authentication.getName();
        try {
            UUID userId = UUID.fromString(principalName);
            if (userRepository.existsById(userId)) {
                return userId;
            }
        } catch (IllegalArgumentException ignored) {
        }

        User user = userRepository
                .findByUsername(principalName)
                .orElseThrow(() ->
                        new org.springframework.web.server
                                .ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found: " + principalName
                        )
                );

        return user.getId();
    }

    private ChildResponse toResponse(Child child) {
        User childUser = userRepository
                .findById(child.getUserId())
                .orElseThrow(() ->
                        new org.springframework.web.server
                                .ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Child user not found"
                        )
                );

        return new ChildResponse(
                child.getUserId(),
                childUser.getUsername(),
                child.getDisplayName(),
                child.getBirthYear(),
                "LOCKED".equalsIgnoreCase(childUser.getStatus())
        );
    }
}
