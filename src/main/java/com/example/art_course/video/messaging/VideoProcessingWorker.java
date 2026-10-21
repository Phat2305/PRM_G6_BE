
package com.example.art_course.video.messaging;

import com.example.art_course.video.config.VideoRabbitConfig;
import com.example.art_course.video.dto.VideoProcessingMessage;
import com.example.art_course.video.service.VideoProcessingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class VideoProcessingWorker {

    private final VideoProcessingService videoProcessingService;

    public VideoProcessingWorker(
            VideoProcessingService videoProcessingService) {
        this.videoProcessingService = videoProcessingService;
    }

    @RabbitListener(queues = VideoRabbitConfig.VIDEO_QUEUE)
    public void processVideo(VideoProcessingMessage message) {
        System.out.println(
                "Received video processing job: " + message.getVideoId());

        String hlsId = videoProcessingService.convertToHls(
                message.getInputPath());

        System.out.println(
                "Video processing completed. Video ID: "
                        + message.getVideoId()
                        + ", HLS ID: "
                        + hlsId);
    }
}
