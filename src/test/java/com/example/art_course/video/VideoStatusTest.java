package com.example.art_course.video;

import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.user.repository.UserRepository;
import com.example.art_course.video.dto.VideoProcessingMessage;
import com.example.art_course.video.messaging.VideoProcessingPublisher;
import com.example.art_course.video.messaging.VideoProcessingWorker;
import com.example.art_course.video.service.R2StorageService;
import com.example.art_course.video.service.VideoLessonService;
import com.example.art_course.video.service.VideoProcessingService;
import com.example.art_course.video.service.VideoStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VideoStatusTest {

    private final UUID lessonId = UUID.randomUUID();
    private final VideoProcessingService processing = mock(VideoProcessingService.class);
    private final VideoLessonService lessons = mock(VideoLessonService.class);
    private final VideoProcessingWorker worker = new VideoProcessingWorker(processing, lessons);
    private final VideoProcessingMessage message =
            new VideoProcessingMessage("v1", "in.mp4", lessonId.toString());

    @Test
    void workerMarksProcessingThenReady() {
        when(processing.convert("in.mp4"))
                .thenReturn(new VideoProcessingService.HlsResult("hls1", 125));

        worker.processVideo(message);

        verify(lessons).markProcessing(lessonId);
        verify(lessons).completeLessonVideo(lessonId, "hls1", 125);
        verify(lessons, never()).markFailed(any(), any());
    }

    @Test
    void workerMarksFailedAndRethrows() {
        ResponseStatusException error = new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT, "Video processing timed out");
        when(processing.convert("in.mp4")).thenThrow(error);

        assertThrows(ResponseStatusException.class, () -> worker.processVideo(message));

        verify(lessons).markFailed(lessonId, error);
        verify(lessons, never()).completeLessonVideo(any(), any(), any());
    }

    @Test
    void markFailedStoresOnlySafeReason() {
        LessonRepository repo = mock(LessonRepository.class);
        VideoLessonService service = new VideoLessonService(repo,
                mock(CourseRepository.class), mock(UserRepository.class),
                mock(VideoStorageService.class), mock(VideoProcessingPublisher.class),
                mock(R2StorageService.class));
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        when(repo.findById(lessonId)).thenReturn(Optional.of(lesson));

        service.markFailed(lessonId, new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Video conversion failed. Check ffmpeg.log"));

        assertEquals("FAILED", lesson.getVideoStatus());
        assertEquals("Video conversion failed. Check ffmpeg.log", lesson.getVideoError());

        service.markFailed(lessonId, new IllegalStateException("secret internals"));
        assertEquals("Video processing failed", lesson.getVideoError());
    }

    @Test
    void deleteVideoRemovesHlsFolderAndClearsLesson() {
        LessonRepository repo = mock(LessonRepository.class);
        CourseRepository courses = mock(CourseRepository.class);
        UserRepository users = mock(UserRepository.class);
        R2StorageService r2 = mock(R2StorageService.class);
        VideoLessonService service = new VideoLessonService(repo, courses, users,
                mock(VideoStorageService.class), mock(VideoProcessingPublisher.class), r2);
        java.util.UUID teacherId = java.util.UUID.randomUUID();
        java.util.UUID courseId = java.util.UUID.randomUUID();
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setCourseId(courseId);
        lesson.setVideoUrl("/api/videos/hls/abc-123/master.m3u8");
        lesson.setVideoStatus("READY");
        lesson.setDurationSeconds(90);
        com.example.art_course.course.entity.Course course = new com.example.art_course.course.entity.Course();
        course.setTeacherId(teacherId);
        com.example.art_course.user.entity.User teacher = new com.example.art_course.user.entity.User();
        teacher.setId(teacherId);
        when(repo.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(users.findById(teacherId)).thenReturn(Optional.of(teacher));

        service.deleteLessonVideo(lessonId, teacherId.toString());

        verify(r2).deletePrefix("hls/abc-123/");
        assertNull(lesson.getVideoUrl());
        assertNull(lesson.getVideoStatus());
        assertNull(lesson.getDurationSeconds());

        lesson.setVideoStatus("PROCESSING");
        assertThrows(com.example.art_course.common.AppError.class,
                () -> service.deleteLessonVideo(lessonId, teacherId.toString()));
        assertNull(VideoLessonService.hlsIdOf("/api/videos/hls/../x/master.m3u8"));
    }
}
