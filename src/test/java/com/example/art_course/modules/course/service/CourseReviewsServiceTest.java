package com.example.art_course.modules.course.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.contracts.IVideoAccessContract;
import com.example.art_course.modules.course.dto.CourseSummaryDto;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.modules.course.repository.CourseReviewRepository;
import com.example.art_course.modules.course.repository.CourseReviewRepository.RatingSummary;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    private final CourseService service = new CourseService(
            courseRepo, reviewRepo, mock(ApplicationEventPublisher.class), mock(EntityManager.class),
            mock(IVideoAccessContract.class), names);

    private final UUID teacherId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

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
