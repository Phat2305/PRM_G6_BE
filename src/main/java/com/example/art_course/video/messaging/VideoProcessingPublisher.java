
package com.example.art_course.video.messaging;

import com.example.art_course.video.config.VideoRabbitConfig;
import com.example.art_course.video.dto.VideoProcessingMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class VideoProcessingPublisher {

    private final RabbitTemplate rabbitTemplate;

    public VideoProcessingPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(VideoProcessingMessage message) {
        rabbitTemplate.convertAndSend(
                VideoRabbitConfig.VIDEO_EXCHANGE,
                VideoRabbitConfig.VIDEO_ROUTING_KEY,
                message
        );
    }
}
