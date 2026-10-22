
package com.example.art_course.video.controller;

import com.example.art_course.video.dto.VideoProcessingMessage;
import com.example.art_course.video.messaging.VideoProcessingPublisher;
import com.example.art_course.video.service.VideoLessonService;
import com.example.art_course.video.service.VideoStorageService;
import com.example.art_course.video.service.VideoStorageService.StoredVideo;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoStorageService storageService;
    private final VideoProcessingPublisher videoProcessingPublisher;
    private final VideoLessonService videoLessonService;

    public VideoController(
            VideoStorageService storageService,
            VideoProcessingPublisher videoProcessingPublisher,
            VideoLessonService videoLessonService
    ) {
        this.storageService = storageService;
        this.videoProcessingPublisher = videoProcessingPublisher;
        this.videoLessonService = videoLessonService;
    }

    /**
     * Upload a video and enqueue HLS processing through RabbitMQ.
     */
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<UploadVideoResponse> upload(
            @RequestParam("file") MultipartFile file
    ) {
        StoredVideo storedVideo = storageService.store(file);

        String videoId = UUID.randomUUID().toString();

        VideoProcessingMessage message = new VideoProcessingMessage(
                videoId,
                storedVideo.absolutePath()
        );

        videoProcessingPublisher.publish(message);

        UploadVideoResponse response = new UploadVideoResponse(
                videoId,
                storedVideo.fileName(),
                "QUEUED",
                null
        );

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }

    /**
     * Upload a video directly to a lesson.
     * This endpoint remains synchronous.
     */
    @PostMapping(
            value = "/lessons/{lessonId}/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, String>> uploadLessonVideo(
            @PathVariable UUID lessonId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String videoId = videoLessonService.queueLessonVideo(
                lessonId,
                file,
                authentication.getName()
        );

        return ResponseEntity.accepted().body(Map.of(
                "lessonId", lessonId.toString(),
                "videoId", videoId,
                "status", "QUEUED"
        ));
    }

    public record UploadVideoResponse(
            String videoId,
            String originalFileName,
            String status,
            String hlsId
    ) {
    }
}
