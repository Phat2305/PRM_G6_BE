
        package com.example.art_course.child_progress.service;

import com.example.art_course.child_progress.dto.ChildProgressResponse;
import com.example.art_course.child_progress.dto.UpdateProgressRequest;
import com.example.art_course.child_progress.entity.ChildProgress;
import com.example.art_course.child_progress.entity.ChildProgressId;
import com.example.art_course.child_progress.repository.ChildProgressRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ChildProgressService {

    private final ChildProgressRepository progressRepository;

    public ChildProgressService(
            ChildProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
    }

    public List<ChildProgressResponse> getByChildId(UUID childId) {
        return progressRepository.findAllByIdChildId(childId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ChildProgressResponse getProgress(
            UUID childId,
            UUID lessonId) {

        ChildProgress progress = progressRepository
                .findByIdChildIdAndIdLessonId(childId, lessonId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Progress not found"));

        return toResponse(progress);
    }

    @Transactional
    public ChildProgressResponse updateProgress(
            UUID childId,
            UUID lessonId,
            UpdateProgressRequest request) {

        if (request.getWatchedSeconds() == null
                || request.getWatchedSeconds() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Watched seconds must be non-negative");
        }

        ChildProgressId id =
                new ChildProgressId(childId, lessonId);

        ChildProgress progress = progressRepository.findById(id)
                .orElseGet(() -> ChildProgress.builder()
                        .id(id)
                        .watchedSeconds(0)
                        .isCompleted(false)
                        .build());

        // Do not decrease the previously recorded watch time.
        progress.setWatchedSeconds(Math.max(
                progress.getWatchedSeconds(),
                request.getWatchedSeconds()
        ));

        if (Boolean.TRUE.equals(request.getIsCompleted())) {
            progress.setIsCompleted(true);
        }

        return toResponse(progressRepository.save(progress));
    }

    private ChildProgressResponse toResponse(
            ChildProgress progress) {

        return ChildProgressResponse.builder()
                .childId(progress.getId().getChildId())
                .lessonId(progress.getId().getLessonId())
                .watchedSeconds(progress.getWatchedSeconds())
                .isCompleted(progress.getIsCompleted())
                .build();
    }
}