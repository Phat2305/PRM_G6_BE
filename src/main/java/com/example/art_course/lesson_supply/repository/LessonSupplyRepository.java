package com.example.art_course.lesson_supply.repository;

import com.example.art_course.lesson_supply.entity.LessonSupply;
import com.example.art_course.lesson_supply.entity.LessonSupplyId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonSupplyRepository extends JpaRepository<LessonSupply, LessonSupplyId> {
    List<LessonSupply> findAllByIdLessonId(java.util.UUID lessonId);
    List<LessonSupply> findAllByIdSupplyId(java.util.UUID supplyId);

}
