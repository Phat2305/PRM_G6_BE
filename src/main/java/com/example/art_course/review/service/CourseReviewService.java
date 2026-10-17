
        package com.example.art_course.review.service;

import com.example.art_course.review.dto.CourseReviewResponse;
import com.example.art_course.review.dto.CreateCourseReviewRequest;
import com.example.art_course.review.dto.UpdateCourseReviewRequest;
import com.example.art_course.review.entity.CourseReview;
import com.example.art_course.review.repository.CourseReviewRepository;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CourseReviewService {

    private final CourseReviewRepository repository;
    private final com.example.art_course.child.repository.ChildRepository childRepository;

    public CourseReviewService(
            CourseReviewRepository repository,
            com.example.art_course.child.repository.ChildRepository childRepository) {
        this.repository = repository;
        this.childRepository = childRepository;
    }

    @Transactional
    public CourseReviewResponse create(
            CreateCourseReviewRequest request) {

        // Prevent duplicate reviews for the same course and child.
        if (repository.findByCourseIdAndChildId(
                request.getCourseId(),
                request.getChildId()).isPresent()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This child already has a review for the course");
        }

        UUID parentId = request.getParentId();
        if (parentId == null && request.getChildId() != null) {
            parentId = childRepository.findById(request.getChildId())
                    .map(com.example.art_course.child.entity.Child::getParentId)
                    .orElse(null);
        }

        CourseReview review = CourseReview.builder()
                .courseId(request.getCourseId())
                .childId(request.getChildId())
                .parentId(parentId)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        return toResponse(repository.save(review));
    }

    public CourseReviewResponse getById(UUID id) {
        return toResponse(findById(id));
    }

    public List<CourseReviewResponse> getByCourseId(UUID courseId) {
        return repository.findAllByCourseId(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<CourseReviewResponse> getByChildId(UUID childId) {
        return repository.findAllByChildId(childId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<CourseReviewResponse> getByParentId(UUID parentId) {
        return repository.findAllByParentId(parentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CourseReviewResponse update(
            UUID id,
            UpdateCourseReviewRequest request) {

        CourseReview review = findById(id);

        review.setRating(request.getRating());
        review.setComment(request.getComment());

        return toResponse(repository.save(review));
    }

    @Transactional
    public void delete(UUID id) {
        CourseReview review = findById(id);
        repository.delete(review);
    }

    private CourseReview findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Course review not found"));
    }

    private CourseReviewResponse toResponse(CourseReview review) {
        return CourseReviewResponse.builder()
                .id(review.getId())
                .courseId(review.getCourseId())
                .childId(review.getChildId())
                .parentId(review.getParentId())
                .rating(review.getRating())
                .comment(review.getComment())
                .build();
    }
}
