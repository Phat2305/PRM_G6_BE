
package com.example.art_course.child.service;

import com.example.art_course.child.dto.CreateChildRequest;
import com.example.art_course.child.dto.UpdateChildRequest;
import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ChildService {

    private final ChildRepository childRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ChildService(
            ChildRepository childRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.childRepository = childRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Child createChild(
            UUID parentId,
            CreateChildRequest request
    ) {
        User parent = userRepository.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parent not found"
                ));

        if (!"PARENT".equalsIgnoreCase(parent.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only a parent can create a child account"
            );
        }

        // 1. Resolve Display Name (required in DB)
        String displayName = request.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
                displayName = request.getUsername().trim();
            } else {
                displayName = "Bé yêu";
            }
        } else {
            displayName = displayName.trim();
        }

        // 2. Resolve Birth Year
        Short birthYear = request.getBirthYear();
        if (birthYear == null && request.getBirthDate() != null && !request.getBirthDate().trim().isEmpty()) {
            try {
                String bd = request.getBirthDate().trim();
                if (bd.length() >= 4) {
                    short parsedYear = Short.parseShort(bd.substring(0, 4));
                    if (parsedYear >= 1900 && parsedYear <= 2100) {
                        birthYear = parsedYear;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Resolve Username
        String username = request.getUsername();
        if (username == null || username.trim().isEmpty()) {
            username = "child_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            while (userRepository.existsByUsername(username)) {
                username = "child_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            }
        } else {
            username = username.trim();
            if (userRepository.existsByUsername(username)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Username already exists: " + username
                );
            }
        }

        // 4. Password is required: no shared default, a guessable default would open every child account
        String password = request.getPassword();
        if (password == null || password.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password is required"
            );
        }

        User childUser = new User();
        childUser.setUsername(username);
        childUser.setPassword(
                passwordEncoder.encode(password)
        );
        childUser.setRole("CHILD");
        childUser.setStatus("ACTIVE");

        User savedUser = userRepository.save(childUser);

        Child child = Child.builder()
                .userId(savedUser.getId())
                .parentId(parent.getId())
                .displayName(displayName)
                .birthYear(birthYear)
                .build();

        return childRepository.save(child);
    }

    @Transactional(readOnly = true)
    public List<Child> getChildren(UUID parentId) {
        return childRepository.findAllByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public Child getChild(UUID parentId, UUID userId) {
        return childRepository
                .findByUserIdAndParentId(userId, parentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Child not found"
                ));
    }

    @Transactional
    public Child updateChild(
            UUID parentId,
            UUID userId,
            UpdateChildRequest request
    ) {
        Child child = getChild(parentId, userId);

        if (request.getDisplayName() != null && !request.getDisplayName().trim().isEmpty()) {
            child.setDisplayName(request.getDisplayName().trim());
        }

        Short birthYear = request.getBirthYear();
        if (birthYear == null && request.getBirthDate() != null && !request.getBirthDate().trim().isEmpty()) {
            try {
                String bd = request.getBirthDate().trim();
                if (bd.length() >= 4) {
                    short parsedYear = Short.parseShort(bd.substring(0, 4));
                    if (parsedYear >= 1900 && parsedYear <= 2100) {
                        birthYear = parsedYear;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (birthYear != null) {
            child.setBirthYear(birthYear);
        }

        return childRepository.save(child);
    }

    @Transactional
    public void resetPassword(UUID parentId, UUID userId, String newPassword) {
        Child child = getChild(parentId, userId);
        User user = findChildUser(child);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.revokeTokens();
        userRepository.save(user);
    }

    // Only ACTIVE <-> LOCKED is the parent's call; a child suspended by an admin stays suspended.
    @Transactional
    public Child setLocked(UUID parentId, UUID userId, boolean locked) {
        Child child = getChild(parentId, userId);
        User user = findChildUser(child);

        String target = locked ? "LOCKED" : "ACTIVE";
        String current = user.getStatus();
        if (target.equalsIgnoreCase(current)) {
            return child;
        }
        if (!"ACTIVE".equalsIgnoreCase(current) && !"LOCKED".equalsIgnoreCase(current)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Child account is " + current + " and cannot be changed by the parent"
            );
        }

        user.setStatus(target);
        userRepository.save(user);
        return child;
    }

    private User findChildUser(Child child) {
        return userRepository.findById(child.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Child user not found"
                ));
    }

    @Transactional
    public void deleteChild(UUID parentId, UUID userId) {
        Child child = getChild(parentId, userId);

        childRepository.delete(child);
        userRepository.deleteById(child.getUserId());
    }
}
