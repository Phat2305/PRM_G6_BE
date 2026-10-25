
package com.example.art_course.video.messaging;

import com.example.art_course.video.config.VideoRabbitConfig;
import com.example.art_course.video.dto.VideoProcessingMessage;
import com.example.art_course.video.service.VideoLessonService;
import com.example.art_course.video.service.VideoProcessingService;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VideoProcessingWorker {

    private final VideoProcessingService videoProcessingService;
    private final VideoLessonService videoLessonService;

    public VideoProcessingWorker(
            VideoProcessingService videoProcessingService,
            VideoLessonService videoLessonService) {
        this.videoProcessingService = videoProcessingService;
        this.videoLessonService = videoLessonService;
    }

    @RabbitListener(queues = VideoRabbitConfig.VIDEO_QUEUE)
    public void processVideo(VideoProcessingMessage message) {
        System.out.println(
                "Received video processing job: " + message.getVideoId());

        UUID lessonId = message.getLessonId() != null
                && !message.getLessonId().isBlank()
                ? UUID.fromString(message.getLessonId())
                : null;

        String hlsId;

        if (lessonId == null) {
            hlsId = videoProcessingService.convertToHls(
                    message.getInputPath());
        } else {
            videoLessonService.markProcessing(lessonId);
            try {
                VideoProcessingService.HlsResult result =
                        videoProcessingService.convert(message.getInputPath());
                hlsId = result.hlsId();
                videoLessonService.completeLessonVideo(
                        lessonId, hlsId, result.durationSeconds());
            } catch (RuntimeException e) {
                // Ghi FAILED rồi ném lại để container reject message như cũ.
                videoLessonService.markFailed(lessonId, e);
                throw e;
            }
        }

        System.out.println(
                "Video processing completed. Video ID: "
                        + message.getVideoId()
                        + ", HLS ID: " + hlsId);
    }
}
