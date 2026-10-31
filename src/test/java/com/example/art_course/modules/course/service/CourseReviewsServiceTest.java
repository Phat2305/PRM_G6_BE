package com.example.art_course.modules.course.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.contracts.IVideoAccessContract;
import com.example.art_course.modules.course.dto.CourseSummaryDto;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.contracts.events.CourseReviewedEvent;
import com.example.art_course.modules.course.entity.CourseReview;
import com.example.art_course.modules.course.repository.CourseReviewRepository;
import com.example.art_course.modules.course.repository.CourseReviewRepository.RatingSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Khóa học: điểm đánh giá trong danh mục và quyền xem đánh giá của giáo viên")
class CourseReviewsServiceTest {

    private final CourseRepository courseRepo = mock(CourseRepository.class);
    private final CourseReviewRepository reviewRepo = mock(CourseReviewRepository.class);
    private final TeacherNames names = mock(TeacherNames.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CourseService service = new CourseService(
            courseRepo, reviewRepo, events, entityManager,
            mock(IVideoAccessContract.class), names,
            mock(com.example.art_course.modules.order.repository.OrderDetailRepository.class));

    private final UUID teacherId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private final UUID childId = UUID.randomUUID();

    private CustomizeRequest customize(String status) {
        CustomizeRequest request = CustomizeRequest.builder().id(UUID.randomUUID())
                .parentId(parentId).status(status).build();
        Course course = course(courseId);
        course.setCustomizeRequestId(request.getId());
        when(courseRepo.findById(courseId)).thenReturn(Optional.of(course));
        when(entityManager.find(CustomizeRequest.class, request.getId())).thenReturn(request);
        return request;
    }

    private void enrollment(List<UUID> children) {
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(children);
        when(reviewRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "OPEN", "ACCEPTED", "FUNDED", "DELIVERED", "REJECTED", "CANCELLED"})
    void customizeCannotBeReviewedBeforeParentAcceptance(String status) {
        customize(status);

        AppError error = assertThrows(AppError.class,
                () -> service.createCourseReview(courseId, parentId, 5, "Hay"));

        assertEquals("NOT_ELIGIBLE", error.getErrorCode());
        verify(entityManager, never()).createNativeQuery(anyString());
        verifyNoInteractions(reviewRepo, events);
    }

    @Test
    void completedCustomizeRequiresItsParentAndAnExistingRequest() {
        CustomizeRequest request = customize("COMPLETED");
        assertThrows(AppError.class,
                () -> service.createCourseReview(courseId, UUID.randomUUID(), 5, "Hay"));
        when(entityManager.find(CustomizeRequest.class, request.getId())).thenReturn(null);
        assertThrows(AppError.class,
                () -> service.createCourseReview(courseId, parentId, 5, "Hay"));
        verifyNoInteractions(reviewRepo, events);
    }

    @Test
    void acceptedCustomizeStillRequiresAnActivelyEnrolledChild() {
        customize("COMPLETED");
        enrollment(List.of());
        assertEquals("NOT_ELIGIBLE", assertThrows(AppError.class,
                () -> service.createCourseReview(courseId, parentId, 5, "Hay")).getErrorCode());
        verifyNoInteractions(reviewRepo, events);
    }

    @Test
    void completedCustomizeSavesReviewForTheEnrolledCourseAndPublishesEvent() {
        customize("COMPLETED");
        enrollment(List.of(childId));

        CourseReview review = service.createCourseReview(courseId, parentId, 5, "Hay");

        assertEquals(courseId, review.getCourseId());
        assertEquals(parentId, review.getParentId());
        assertEquals(childId, review.getChildId());
        verify(reviewRepo).save(review);
        verify(events).publishEvent(any(CourseReviewedEvent.class));
    }

    @Test
    void ordinaryEnrolledCourseCanStillBeReviewedWithoutCustomizeAcceptance() {
        when(courseRepo.findById(courseId)).thenReturn(Optional.of(course(courseId)));
        enrollment(List.of(childId));
        assertEquals(courseId, service.createCourseReview(courseId, parentId, 4, "Ổn").getCourseId());
        verify(entityManager, never()).find(eq(CustomizeRequest.class), any());
    }

    @Test
    void completedCustomizeStillRejectsDuplicateReviews() {
        customize("COMPLETED");
        enrollment(List.of(childId));
        when(reviewRepo.findByCourseIdAndParentId(courseId, parentId))
                .thenReturn(Optional.of(CourseReview.builder().build()));
        assertEquals("ALREADY_REVIEWED", assertThrows(AppError.class,
                () -> service.createCourseReview(courseId, parentId, 5, "Hay")).getErrorCode());
        verify(reviewRepo, never()).save(any());
        verifyNoInteractions(events);
    }

    private static RatingSummary summary(UUID courseId, double average, long total) {
        return new RatingSummary() {
            public UUID getCourseId() { return courseId; }
            public Double getAverage() { return average; }
            public Long getTotal() { return total; }
        };
    }

    private Course course(UUID id) {
        return Course.builder().id(id).teacherId(teacherId).name("Màu nước").price(new BigDecimal("1000"))
                .status("PUBLISHED").build();
    }

    @Test
    @DisplayName("danh mục: có điểm trung bình làm tròn 1 chữ số và số đánh giá; khóa chưa có đánh giá thì null và 0")
    void catalogCarriesRatings() {
        UUID rated = UUID.randomUUID();
        UUID unrated = UUID.randomUUID();
        when(courseRepo.findByStatusIgnoreCase("PUBLISHED")).thenReturn(List.of(course(rated), course(unrated)));
        when(names.byIds(any())).thenReturn(Map.of());
        when(reviewRepo.summarize(any())).thenReturn(List.of(summary(rated, 4.3333, 3)));

        List<CourseSummaryDto> list = service.listCourses();

        CourseSummaryDto first = list.stream().filter(c -> c.getId().equals(rated)).findFirst().orElseThrow();
        CourseSummaryDto second = list.stream().filter(c -> c.getId().equals(unrated)).findFirst().orElseThrow();
        assertEquals(4.3, first.getAverageRating());
        assertEquals(3, first.getReviewCount());
        assertNull(second.getAverageRating());
        assertEquals(0, second.getReviewCount());
    }

    @Test
    @DisplayName("danh mục phân trang: lọc theo từ khóa và thể loại, trang sau không trùng trang trước")
    void catalogPaginatesAndFilters() {
        List<Course> courses = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            courses.add(Course.builder().id(UUID.randomUUID()).teacherId(teacherId)
                    .name(String.format("Màu nước %02d", i)).category("Màu nước")
                    .price(new BigDecimal("1000")).status("PUBLISHED").build());
        }
        courses.add(Course.builder().id(UUID.randomUUID()).teacherId(teacherId).name("Chì cơ bản")
                .category("Chì").price(new BigDecimal("1000")).status("PUBLISHED").build());
        when(courseRepo.findByStatusIgnoreCase("PUBLISHED")).thenReturn(courses);
        when(names.byIds(any())).thenReturn(Map.of(teacherId, "Cô Lan"));
        when(reviewRepo.summarize(any())).thenReturn(List.of());

        var first = service.listCourses("màu nước", null, 0, 10);
        var second = service.listCourses("màu nước", null, 1, 10);
        assertEquals(12, first.total());
        assertEquals(10, first.items().size());
        assertTrue(first.hasMore());
        assertEquals(2, second.items().size());
        assertFalse(second.hasMore());
        assertTrue(first.items().stream().noneMatch(a -> second.items().stream()
                .anyMatch(b -> b.getId().equals(a.getId()))));

        assertEquals(1, service.listCourses(null, "Chì", 0, 10).total());
        assertEquals(13, service.listCourses("cô lan", null, null, null).items().size());
    }

    @Test
    @DisplayName("danh mục rỗng: không gọi truy vấn tổng hợp")
    void emptyCatalogSkipsAggregate() {
        when(courseRepo.findByStatusIgnoreCase("PUBLISHED")).thenReturn(List.of());
        when(names.byIds(any())).thenReturn(Map.of());

        assertTrue(service.listCourses().isEmpty());
        verify(reviewRepo, never()).summarize(any());
    }

    @Test
    @DisplayName("giáo viên của khóa xem được đánh giá; giáo viên khác hoặc khóa không tồn tại bị 403 như nhau")
    void onlyOwnerTeacherReadsReviews() {
        when(courseRepo.findById(courseId)).thenReturn(Optional.of(course(courseId)));
        when(reviewRepo.findByCourseIdOrderByCreatedAtDesc(courseId)).thenReturn(List.of());

        assertNotNull(service.getCourseReviewsForTeacher(courseId, teacherId));

        AppError other = assertThrows(AppError.class,
                () -> service.getCourseReviewsForTeacher(courseId, UUID.randomUUID()));
        assertEquals(403, other.getStatus().value());
        AppError missing = assertThrows(AppError.class,
                () -> service.getCourseReviewsForTeacher(UUID.randomUUID(), teacherId));
        assertEquals(403, missing.getStatus().value());
    }
}
