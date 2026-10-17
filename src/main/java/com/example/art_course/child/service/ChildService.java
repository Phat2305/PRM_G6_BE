
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

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        User childUser = new User();
        childUser.setUsername(request.getUsername());
        childUser.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        childUser.setRole("CHILD");
        childUser.setStatus("ACTIVE");

        User savedUser = userRepository.save(childUser);

        Child child = Child.builder()
                .userId(savedUser.getId())
                .parentId(parent.getId())
                .displayName(request.getDisplayName())
                .birthYear(request.getBirthYear())
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

        if (request.getDisplayName() != null) {
            child.setDisplayName(request.getDisplayName());
        }

        if (request.getBirthYear() != null) {
            child.setBirthYear(request.getBirthYear());
        }

        return childRepository.save(child);
    }

    @Transactional
    public void deleteChild(UUID parentId, UUID userId) {
        Child child = getChild(parentId, userId);

        childRepository.delete(child);
        userRepository.deleteById(child.getUserId());
    }
}
