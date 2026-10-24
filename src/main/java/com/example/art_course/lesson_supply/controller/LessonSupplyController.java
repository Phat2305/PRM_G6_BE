
package com.example.art_course.lesson_supply.controller;

import com.example.art_course.lesson_supply.dto.AddLessonSupplyRequest;
import com.example.art_course.lesson_supply.dto.LessonSupplyResponse;
import com.example.art_course.lesson_supply.dto.UpdateLessonSupplyRequest;
import com.example.art_course.lesson_supply.entity.LessonSupply;
import com.example.art_course.lesson_supply.service.LessonSupplyService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lesson-supplies")
public class LessonSupplyController {

    private final LessonSupplyService service;

    public LessonSupplyController(LessonSupplyService service) {
        this.service = service;
    }

    @GetMapping("/lesson/{lessonId}")
    public List<LessonSupplyResponse> getByLessonId(
            @PathVariable UUID lessonId
    ) {
        return service.getByLessonId(lessonId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LessonSupplyResponse add(
            @Valid @RequestBody AddLessonSupplyRequest request,
            Authentication authentication
    ) {
        return toResponse(service.add(request, authentication.getName()));
    }

    @PutMapping("/lesson/{lessonId}/supply/{supplyId}")
    public LessonSupplyResponse updateQuantity(
            @PathVariable UUID lessonId,
            @PathVariable UUID supplyId,
            @Valid @RequestBody UpdateLessonSupplyRequest request,
            Authentication authentication
    ) {
        return toResponse(
                service.updateQuantity(
                        lessonId, supplyId, request, authentication.getName())
        );
    }

    @DeleteMapping("/lesson/{lessonId}/supply/{supplyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable UUID lessonId,
            @PathVariable UUID supplyId,
            Authentication authentication
    ) {
        service.remove(lessonId, supplyId, authentication.getName());
    }

    private LessonSupplyResponse toResponse(LessonSupply entity) {
        return new LessonSupplyResponse(
                entity.getId().getLessonId(),
                entity.getId().getSupplyId(),
                entity.getQuantity()
        );
    }
}
