
package com.example.art_course.lesson_supply.service;

import com.example.art_course.lesson_supply.dto.AddLessonSupplyRequest;
import com.example.art_course.lesson_supply.dto.UpdateLessonSupplyRequest;
import com.example.art_course.lesson_supply.entity.LessonSupply;
import com.example.art_course.lesson_supply.entity.LessonSupplyId;
import com.example.art_course.lesson_supply.repository.LessonSupplyRepository;

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

    public LessonSupplyService(LessonSupplyRepository repository) {
        this.repository = repository;
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
    public LessonSupply add(AddLessonSupplyRequest request) {
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
            UpdateLessonSupplyRequest request
    ) {
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
    public void remove(UUID lessonId, UUID supplyId) {
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
