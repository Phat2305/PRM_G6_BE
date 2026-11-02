package com.example.art_course.common;

import com.example.art_course.common.image.ImageService;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.video.service.R2StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Ảnh dụng cụ / ảnh bìa: chỉ giáo viên sở hữu, chỉ JPG/PNG/WEBP ≤ 5 MB, đổi ảnh thì chờ duyệt lại")
class ImageServiceTest {

    private final SupplyRepository supplies = mock(SupplyRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final R2StorageService r2 = mock(R2StorageService.class);
    private final com.example.art_course.course.service.CourseService courseService =
            mock(com.example.art_course.course.service.CourseService.class);
    private final ImageService service = new ImageService(supplies, courses, lessons, r2, courseService);
    private final UUID teacherId = UUID.randomUUID();
    private final MockMultipartFile png = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});

    @Test
    @DisplayName("dụng cụ: chủ tải ảnh lên thì lưu R2, đặt URL công khai và quay về chờ duyệt; xóa ảnh cũ của mình")
    void supplyImage() {
        UUID oldId = UUID.randomUUID();
        Supply supply = new Supply();
        supply.setTeacherId(teacherId);
        supply.setStatus("ACTIVE");
        supply.setImageUrl("/api/images/supplies/" + oldId);
        UUID id = UUID.randomUUID();
        when(supplies.findById(id)).thenReturn(Optional.of(supply));

        String url = service.uploadSupplyImage(id, teacherId, png);

        assertTrue(url.startsWith("/api/images/supplies/"));
        assertEquals(url, supply.getImageUrl());
        assertEquals("PENDING_REVIEW", supply.getStatus());
        verify(r2).uploadBytes(startsWith("images/supplies/"), any(), eq("image/png"));
        verify(r2).deleteObject("images/supplies/" + oldId);
    }

    @Test
    @DisplayName("ảnh ngoài (nhập tay trước đây) không bị xóa trên R2")
    void externalUrlIsKept() {
        Course course = new Course();
        course.setTeacherId(teacherId);
        course.setCoverImageUrl("https://example.com/a.png");
        UUID id = UUID.randomUUID();
        when(courses.findById(id)).thenReturn(Optional.of(course));

        service.uploadCourseCover(id, teacherId, png);

        verify(courseService).prepareCustomizeEdit(course);
        verify(r2, never()).deleteObject(any());
        assertTrue(course.getCoverImageUrl().startsWith("/api/images/courses/"));
    }

    @Test
    void draftCoverIsOnlyVisibleToItsTeacher() {
        UUID imageId = UUID.randomUUID();
        Course draft = Course.builder().teacherId(teacherId).status("DRAFT").build();
        when(courses.findByCoverImageUrl("/api/images/courses/" + imageId)).thenReturn(Optional.of(draft));

        assertEquals(403, assertThrows(AppError.class,
                () -> service.read("courses", imageId, UUID.randomUUID())).getStatus().value());
        service.read("courses", imageId, teacherId);
        verify(r2).getObject("images/courses/" + imageId);
    }

    @Test
    void coverOfACourseWithBuyersCannotBeChanged() {
        UUID id = UUID.randomUUID();
        Course course = Course.builder().teacherId(teacherId).build();
        when(courses.findById(id)).thenReturn(Optional.of(course));
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT)).when(courseService).requireEditable(course);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.uploadCourseCover(id, teacherId, png));
        verifyNoInteractions(r2);
    }

    @Test
    void confirmedCustomizeCoverCannotBeChanged() {
        UUID id = UUID.randomUUID();
        Course course = Course.builder().teacherId(teacherId).customizeRequestId(UUID.randomUUID()).build();
        when(courses.findById(id)).thenReturn(Optional.of(course));
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT)).when(courseService).prepareCustomizeEdit(course);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.uploadCourseCover(id, teacherId, png));
        verifyNoInteractions(r2);
    }

    private Lesson lessonOf(Course course, String sample) {
        UUID courseId = UUID.randomUUID();
        course.setId(courseId);
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).courseId(courseId).hasAssignment(true)
                .sampleImageUrl(sample).build();
        when(lessons.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        return lesson;
    }

    @Test
    @DisplayName("ảnh tranh mẫu: chủ khóa tải lên thì lưu R2, đặt URL công khai, khóa công khai về chờ duyệt, xóa ảnh cũ của mình")
    void lessonSample() {
        UUID oldId = UUID.randomUUID();
        Course course = Course.builder().teacherId(teacherId).status("PUBLISHED").build();
        Lesson lesson = lessonOf(course, "/api/images/lessons/" + oldId);

        String url = service.uploadLessonSample(lesson.getId(), teacherId, png);

        assertTrue(url.startsWith("/api/images/lessons/"));
        assertEquals(url, lesson.getSampleImageUrl());
        assertEquals("PENDING_REVIEW", course.getStatus());
        verify(r2).uploadBytes(startsWith("images/lessons/"), any(), eq("image/png"));
        verify(r2).deleteObject("images/lessons/" + oldId);
        verify(courseService).requireEditable(course);
        verify(courseService).prepareCustomizeEdit(course);
        verify(lessons).save(lesson);
        verify(courses).save(course);
    }

    @Test
    @DisplayName("ảnh tranh mẫu: bài chưa bật bài tập vẽ → 400, không lưu gì")
    void lessonSampleNeedsAnAssignment() {
        Course course = Course.builder().teacherId(teacherId).status("DRAFT").build();
        Lesson lesson = lessonOf(course, null);
        lesson.setHasAssignment(false);

        AppError e = assertThrows(AppError.class, () -> service.uploadLessonSample(lesson.getId(), teacherId, png));

        assertEquals("LESSON_NO_ASSIGNMENT", e.getErrorCode());
        verifyNoInteractions(r2);
        verify(lessons, never()).save(any());
    }

    @Test
    @DisplayName("ảnh tranh mẫu: người khác → 403, khóa đã có người mua → không sửa, bài không tồn tại → 404")
    void lessonSampleRejects() {
        Course course = Course.builder().teacherId(UUID.randomUUID()).status("DRAFT").build();
        Lesson lesson = lessonOf(course, null);
        assertEquals(403, assertThrows(AppError.class,
                () -> service.uploadLessonSample(lesson.getId(), teacherId, png)).getStatus().value());
        assertEquals(403, assertThrows(AppError.class,
                () -> service.deleteLessonSample(lesson.getId(), teacherId)).getStatus().value());

        course.setTeacherId(teacherId);
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT)).when(courseService).requireEditable(course);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.uploadLessonSample(lesson.getId(), teacherId, png));

        assertEquals(404, assertThrows(AppError.class,
                () -> service.uploadLessonSample(UUID.randomUUID(), teacherId, png)).getStatus().value());
        verifyNoInteractions(r2);
    }

    @Test
    @DisplayName("gỡ ảnh tranh mẫu: xóa URL và ảnh trên R2, khóa công khai về chờ duyệt; bài không có ảnh thì không làm gì")
    void lessonSampleDelete() {
        UUID oldId = UUID.randomUUID();
        Course course = Course.builder().teacherId(teacherId).status("PUBLISHED").build();
        Lesson lesson = lessonOf(course, "/api/images/lessons/" + oldId);

        service.deleteLessonSample(lesson.getId(), teacherId);

        assertNull(lesson.getSampleImageUrl());
        assertEquals("PENDING_REVIEW", course.getStatus());
        verify(r2).deleteObject("images/lessons/" + oldId);

        // Gọi lại khi đã hết ảnh: không đổi trạng thái khóa nữa.
        course.setStatus("PUBLISHED");
        service.deleteLessonSample(lesson.getId(), teacherId);
        assertEquals("PUBLISHED", course.getStatus());
        verify(r2, times(1)).deleteObject(any());
    }

    @Test
    @DisplayName("ảnh tranh mẫu của khóa nháp chỉ giáo viên chủ khóa xem được")
    void draftLessonSampleIsOnlyVisibleToItsTeacher() {
        UUID imageId = UUID.randomUUID();
        Course draft = Course.builder().teacherId(teacherId).status("DRAFT").build();
        Lesson lesson = lessonOf(draft, "/api/images/lessons/" + imageId);
        when(lessons.findBySampleImageUrl("/api/images/lessons/" + imageId)).thenReturn(Optional.of(lesson));

        assertEquals(403, assertThrows(AppError.class,
                () -> service.read("lessons", imageId, UUID.randomUUID())).getStatus().value());
        service.read("lessons", imageId, teacherId);
        verify(r2).getObject("images/lessons/" + imageId);

        draft.setStatus("PUBLISHED");
        service.read("lessons", imageId, null);
        verify(r2, times(2)).getObject("images/lessons/" + imageId);
    }

    @Test
    @DisplayName("người khác → 403; sai định dạng → 400; quá lớn → 413")
    void rejects() {
        Supply supply = new Supply();
        supply.setTeacherId(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        when(supplies.findById(id)).thenReturn(Optional.of(supply));
        assertEquals(403, assertThrows(AppError.class,
                () -> service.uploadSupplyImage(id, teacherId, png)).getStatus().value());

        supply.setTeacherId(teacherId);
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1});
        assertEquals(400, assertThrows(AppError.class,
                () -> service.uploadSupplyImage(id, teacherId, gif)).getStatus().value());
        MockMultipartFile big = new MockMultipartFile("file", "a.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertEquals(413, assertThrows(AppError.class,
                () -> service.uploadSupplyImage(id, teacherId, big)).getStatus().value());
        verifyNoInteractions(r2);
    }
}
