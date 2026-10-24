
package com.example.art_course.lesson_supply.service;

import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.lesson_supply.dto.AddLessonSupplyRequest;
import com.example.art_course.lesson_supply.dto.UpdateLessonSupplyRequest;
import com.example.art_course.lesson_supply.entity.LessonSupply;
import com.example.art_course.lesson_supply.entity.LessonSupplyId;
import com.example.art_course.lesson_supply.repository.LessonSupplyRepository;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LessonSupplyService {

    private final LessonSupplyRepository repository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final SupplyRepository supplyRepository;

    public LessonSupplyService(
            LessonSupplyRepository repository,
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            SupplyRepository supplyRepository) {
        this.repository = repository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.supplyRepository = supplyRepository;
    }

    // Chỉ Teacher sở hữu khóa học chứa bài học mới được sửa dụng cụ của bài học.
    private void checkLessonOwner(UUID lessonId, String userId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Lesson not found"));
        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Course not found"));
        if (course.getTeacherId() == null
                || !course.getTeacherId().toString().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to manage supplies of this lesson");
        }
    }

    @Transactional(readOnly = true)
    public List<LessonSupply> getByLessonId(UUID lessonId) {
        if (lessonId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lesson ID is required"
            );
        }

        return repository.findAllByIdLessonId(lessonId);
    }

    @Transactional
    public LessonSupply add(AddLessonSupplyRequest request, String userId) {
        checkLessonOwner(request.getLessonId(), userId);
        Supply supply = supplyRepository.findById(request.getSupplyId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Supply not found"));
        // Chỉ gắn dụng cụ của chính Teacher đó.
        if (supply.getTeacherId() == null
                || !supply.getTeacherId().toString().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only link your own supplies");
        }

        if (request.getQuantity() == null
                || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than zero"
            );
        }

        LessonSupplyId id = new LessonSupplyId(
                request.getLessonId(),
                request.getSupplyId()
        );

        if (repository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Supply is already linked to this lesson"
            );
        }

        LessonSupply lessonSupply = LessonSupply.builder()
                .id(id)
                .quantity(request.getQuantity())
                .build();

        return repository.save(lessonSupply);
    }

    @Transactional
    public LessonSupply updateQuantity(
            UUID lessonId,
            UUID supplyId,
            UpdateLessonSupplyRequest request,
            String userId
    ) {
        checkLessonOwner(lessonId, userId);
        BigDecimal quantity = request.getQuantity();

        if (quantity == null
                || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than zero"
            );
        }

        LessonSupplyId id = new LessonSupplyId(lessonId, supplyId);

        LessonSupply lessonSupply = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lesson supply link not found"
                ));

        lessonSupply.setQuantity(quantity);

        return repository.save(lessonSupply);
    }

    @Transactional
    public void remove(UUID lessonId, UUID supplyId, String userId) {
        checkLessonOwner(lessonId, userId);
        LessonSupplyId id = new LessonSupplyId(lessonId, supplyId);

        if (!repository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Lesson supply link not found"
            );
        }

        repository.deleteById(id);
    }
}
