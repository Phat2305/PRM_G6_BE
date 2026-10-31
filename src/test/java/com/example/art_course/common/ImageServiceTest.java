package com.example.art_course.common;

import com.example.art_course.common.image.ImageService;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
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
    private final R2StorageService r2 = mock(R2StorageService.class);
    private final com.example.art_course.course.service.CourseService courseService =
            mock(com.example.art_course.course.service.CourseService.class);
    private final ImageService service = new ImageService(supplies, courses, r2, courseService);
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
