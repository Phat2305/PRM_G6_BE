package com.example.art_course.video;

import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.user.repository.UserRepository;
import com.example.art_course.video.dto.VideoProcessingMessage;
import com.example.art_course.video.messaging.VideoProcessingPublisher;
import com.example.art_course.video.messaging.VideoProcessingWorker;
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
        when(processing.convertToHls("in.mp4")).thenReturn("hls1");

        worker.processVideo(message);

        verify(lessons).markProcessing(lessonId);
        verify(lessons).completeLessonVideo(lessonId, "hls1");
        verify(lessons, never()).markFailed(any(), any());
    }

    @Test
    void workerMarksFailedAndRethrows() {
        ResponseStatusException error = new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT, "Video processing timed out");
        when(processing.convertToHls("in.mp4")).thenThrow(error);

        assertThrows(ResponseStatusException.class, () -> worker.processVideo(message));

        verify(lessons).markFailed(lessonId, error);
        verify(lessons, never()).completeLessonVideo(any(), any());
    }

    @Test
    void markFailedStoresOnlySafeReason() {
        LessonRepository repo = mock(LessonRepository.class);
        VideoLessonService service = new VideoLessonService(repo,
                mock(CourseRepository.class), mock(UserRepository.class),
                mock(VideoStorageService.class), mock(VideoProcessingPublisher.class));
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
}
