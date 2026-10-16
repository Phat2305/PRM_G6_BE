
package com.example.art_course.video.controller;

import com.example.art_course.video.service.VideoLessonService;
import com.example.art_course.video.service.VideoProcessingService;
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
    private final VideoProcessingService processingService;
    private final VideoLessonService videoLessonService;

    public VideoController(
            VideoStorageService storageService,
            VideoProcessingService processingService,
            VideoLessonService videoLessonService
    ) {
        this.storageService = storageService;
        this.processingService = processingService;
        this.videoLessonService = videoLessonService;
    }

    // Upload MP4 and convert it to HLS
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public UploadVideoResponse upload(
            @RequestParam("file") MultipartFile file
    ) {
        StoredVideo storedVideo = storageService.store(file);

        String hlsId = processingService.convertToHls(
                storedVideo.absolutePath()
        );

        return new UploadVideoResponse(
                storedVideo.fileName(),
                hlsId,
                "READY"
        );
    }


    @PostMapping(
            value = "/lessons/{lessonId}/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, String>> uploadLessonVideo(
            @PathVariable UUID lessonId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String playlistUrl = videoLessonService.uploadLessonVideo(
                lessonId,
                file,
                authentication.getName()
        );

        return ResponseEntity.ok(Map.of(
                "lessonId", lessonId.toString(),
                "videoUrl", playlistUrl,
                "status", "READY"
        ));
    }


    public record UploadVideoResponse(
            String originalFileName,
            String hlsId,
            String status
    ) {
    }
}
