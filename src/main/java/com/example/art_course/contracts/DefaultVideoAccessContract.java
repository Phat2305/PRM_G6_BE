package com.example.art_course.contracts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class DefaultVideoAccessContract implements IVideoAccessContract {

    @Override
    public Optional<String> getSignedTrialVideoUrl(UUID lessonId) {
        log.info("[Standalone Video Access Contract] Giả lập Signed URL cho bài học xem thử: {}", lessonId);
        return Optional.of("https://storage.artcourse.com/videos/mock-trial-" + lessonId + ".mp4?token=mock-signed-url");
    }
}
