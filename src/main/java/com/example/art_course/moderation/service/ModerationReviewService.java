package com.example.art_course.moderation.service;

import com.example.art_course.moderation.dto.CreateModerationReviewRequest;
import com.example.art_course.moderation.dto.ModerationReviewResponse;
import com.example.art_course.moderation.entity.ModerationReview;
import com.example.art_course.moderation.repository.ModerationReviewRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ModerationReviewService {

    private final ModerationReviewRepository repository;
    private final UserRepository userRepository;

    public ModerationReviewService(
            ModerationReviewRepository repository,
            UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ModerationReviewResponse create(
            CreateModerationReviewRequest request,
            String username) {

        User user = findUser(username);
        requireStaff(user);

        if (request.getTargetType() == null
                || request.getTargetType().isBlank()
                || request.getTargetId() == null
                || request.getDecision() == null
                || request.getDecision().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Target type, target ID and decision are required");
        }

        ModerationReview review = ModerationReview.builder()
                .staffId(user.getId())
                .targetType(request.getTargetType().trim())
                .targetId(request.getTargetId())
                .decision(request.getDecision().trim())
                .reason(request.getReason())
                .build();

        return toResponse(repository.save(review));
    }

    public ModerationReviewResponse getById(
            UUID id, String username) {
        requireStaff(findUser(username));
        return toResponse(findById(id));
    }

    public List<ModerationReviewResponse> getByStaffId(
            UUID staffId, String username) {

        User user = findUser(username);
        requireStaff(user);

        if (!isAdmin(user) && !user.getId().equals(staffId)) {
            throw forbidden();
        }

        return repository.findAllByStaffId(staffId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ModerationReviewResponse> getByTarget(
            String targetType,
            UUID targetId,
            String username) {

        requireStaff(findUser(username));

        return repository.findAllByTargetTypeAndTargetId(
                        targetType.trim(), targetId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ModerationReviewResponse> getByTargetType(
            String targetType, String username) {

        requireStaff(findUser(username));

        return repository.findAllByTargetType(targetType.trim())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ModerationReviewResponse> getByDecision(
            String decision, String username) {

        requireStaff(findUser(username));

        return repository.findAllByDecision(decision.trim())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private void requireStaff(User user) {
        if (!isStaff(user) && !isAdmin(user)) {
            throw forbidden();
        }
    }

    private boolean isStaff(User user) {
        return "STAFF".equalsIgnoreCase(user.getRole());
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private ModerationReview findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Moderation review not found"));
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access moderation reviews");
    }

    private ModerationReviewResponse toResponse(
            ModerationReview review) {

        return ModerationReviewResponse.builder()
                .id(review.getId())
                .staffId(review.getStaffId())
                .targetType(review.getTargetType())
                .targetId(review.getTargetId())
                .decision(review.getDecision())
                .reason(review.getReason())
                .createdAt(review.getCreatedAt())
                .build();
    }
}