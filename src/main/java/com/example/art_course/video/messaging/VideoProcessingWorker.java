
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

        String hlsId = videoProcessingService.convertToHls(
                message.getInputPath());

        if (message.getLessonId() != null
                && !message.getLessonId().isBlank()) {
            videoLessonService.completeLessonVideo(
                    UUID.fromString(message.getLessonId()),
                    hlsId);
        }

        System.out.println(
                "Video processing completed. Video ID: "
                        + message.getVideoId()
                        + ", HLS ID: " + hlsId);
    }
}
