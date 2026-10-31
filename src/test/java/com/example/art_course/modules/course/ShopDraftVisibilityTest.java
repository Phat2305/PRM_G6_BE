package com.example.art_course.modules.course;

import com.example.art_course.common.AppError;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.modules.course.service.CourseService;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ShopDraftVisibilityTest {
    private final CourseRepository courses = mock(CourseRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final OrderDetailRepository details = mock(OrderDetailRepository.class);
    private final CourseService service = new CourseService(courses,
            mock(com.example.art_course.modules.course.repository.CourseReviewRepository.class),
            mock(org.springframework.context.ApplicationEventPublisher.class), entityManager,
            mock(com.example.art_course.contracts.IVideoAccessContract.class),
            mock(com.example.art_course.common.TeacherNames.class), details);

    // SHOP-04: chi tiết khóa báo `owned` cho phụ huynh đã trả tiền, khách chưa đăng nhập thì false.
    @Test
    void detailFlagsACourseTheViewingParentAlreadyPaidFor() {
        Course course = Course.builder().id(UUID.randomUUID()).status("PUBLISHED").build();
        UUID parentId = UUID.randomUUID();
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
        TypedQuery<Lesson> lessons = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Lesson.class))).thenReturn(lessons);
        when(lessons.setParameter(anyString(), any())).thenReturn(lessons);
        when(lessons.getResultList()).thenReturn(List.of());
        when(details.parentOwnsCourse(parentId, course.getId())).thenReturn(true);

        assertTrue(service.getCourseDetail(course.getId(), parentId).isOwned());
        assertFalse(service.getCourseDetail(course.getId(), UUID.randomUUID()).isOwned());
        assertFalse(service.getCourseDetail(course.getId()).isOwned());
    }

    @Test
    void shoppingDetailsDoNotExposeDraft() {
        Course draft = Course.builder().id(UUID.randomUUID()).status("DRAFT").build();
        when(courses.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThrows(AppError.class, () -> service.getCourseDetail(draft.getId()));
        verifyNoInteractions(entityManager);
    }

    // CUS-10: khóa làm riêng không bán công khai, dù đã PUBLISHED sau khi Staff duyệt bàn giao.
    @Test
    void shopCatalogAndDetailDoNotExposeCustomizeCourse() {
        Course customize = Course.builder().id(UUID.randomUUID()).teacherId(UUID.randomUUID()).name("Riêng")
                .status("PUBLISHED").customizeRequestId(UUID.randomUUID()).build();
        Course ordinary = Course.builder().id(UUID.randomUUID()).teacherId(UUID.randomUUID()).name("Thường")
                .status("PUBLISHED").build();
        when(courses.findByStatusIgnoreCase("PUBLISHED")).thenReturn(List.of(customize, ordinary));
        when(courses.findById(customize.getId())).thenReturn(Optional.of(customize));

        var items = service.listCourses().stream().map(c -> c.getId()).toList();

        assertEquals(List.of(ordinary.getId()), items);
        assertThrows(AppError.class, () -> service.getCourseDetail(customize.getId()));
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
