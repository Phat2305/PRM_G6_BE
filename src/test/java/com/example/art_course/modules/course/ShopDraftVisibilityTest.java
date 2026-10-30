package com.example.art_course.modules.course;

import com.example.art_course.common.AppError;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.modules.course.service.CourseService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ShopDraftVisibilityTest {
    private final CourseRepository courses = mock(CourseRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CourseService service = new CourseService(courses,
            mock(com.example.art_course.modules.course.repository.CourseReviewRepository.class),
            mock(org.springframework.context.ApplicationEventPublisher.class), entityManager,
            mock(com.example.art_course.contracts.IVideoAccessContract.class),
            mock(com.example.art_course.common.TeacherNames.class));

    @Test
    void shoppingDetailsDoNotExposeDraft() {
        Course draft = Course.builder().id(UUID.randomUUID()).status("DRAFT").build();
        when(courses.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThrows(AppError.class, () -> service.getCourseDetail(draft.getId()));
        verifyNoInteractions(entityManager);
    }

    @Test
    void trialDetailsDoNotExposeDraftLesson() {
        UUID lessonId = UUID.randomUUID();
        Course draft = Course.builder().id(UUID.randomUUID()).status("DRAFT").build();
        when(entityManager.find(Lesson.class, lessonId)).thenReturn(
                Lesson.builder().id(lessonId).courseId(draft.getId()).isTrial(true).build());
        when(courses.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThrows(AppError.class, () -> service.getFreeTrialLesson(lessonId));
    }
}
