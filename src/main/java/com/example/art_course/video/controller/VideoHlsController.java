
package com.example.art_course.video.controller;

import com.example.art_course.video.service.R2StorageService;
import com.example.art_course.video.service.VideoAccessService;
import com.example.art_course.video.service.VideoProcessingService;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos/hls")
public class VideoHlsController {

    private final R2StorageService r2StorageService;
    private final VideoAccessService videoAccessService;

    public VideoHlsController(R2StorageService r2StorageService, VideoAccessService videoAccessService) {
        this.r2StorageService = r2StorageService;
        this.videoAccessService = videoAccessService;
    }

    @GetMapping("/{hlsId}/{fileName}")
    public ResponseEntity<Resource> getHlsFile(
            @PathVariable String hlsId,
            @PathVariable String fileName,
            Authentication authentication) {

        // Validate video ID.
        UUID videoId;
        try {
            videoId = UUID.fromString(hlsId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid video ID");
        }

        // Chỉ cho phép playlist và segment do ffmpeg tạo (cả dạng một mức cũ lẫn nhiều mức mới).
        if (fileName == null
                || !(fileName.matches(VideoProcessingService.PLAYLIST_PATTERN)
                || fileName.matches(VideoProcessingService.SEGMENT_PATTERN))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid HLS file name");
        }

        // LRN-03: chỉ người có quyền xem khóa này mới lấy được playlist/đoạn video (xem VideoAccessService).
        UUID userId = authentication != null && authentication.getPrincipal() instanceof UUID id ? id : null;
        String role = authentication == null ? null : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst().orElse(null);
        if (!videoAccessService.canStream(videoId, userId, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem video này");
        }

        String objectKey = "hls/" + hlsId + "/" + fileName;

        ResponseInputStream<GetObjectResponse> object =
                r2StorageService.getObject(objectKey);

        MediaType mediaType = fileName.endsWith(".m3u8")
                ? MediaType.parseMediaType(
                "application/vnd.apple.mpegurl")
                : MediaType.parseMediaType("video/mp2t");

        Resource resource;

        if (fileName.endsWith(".m3u8")) {
            try (object) {
                String playlist = new String(
                        object.readAllBytes(),
                        StandardCharsets.UTF_8);

                // Keep segment URLs relative to this playlist endpoint.
                resource = new org.springframework.core.io.ByteArrayResource(
                        playlist.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Cannot read HLS playlist from R2",
                        e);
            }
        } else {
            resource = new InputStreamResource(object);
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .body(resource);
    }
}
