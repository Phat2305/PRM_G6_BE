
package com.example.art_course.video.controller;

import com.example.art_course.video.service.R2StorageService;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public VideoHlsController(R2StorageService r2StorageService) {
        this.r2StorageService = r2StorageService;
    }

    @GetMapping("/{hlsId}/{fileName}")
    public ResponseEntity<Resource> getHlsFile(
            @PathVariable String hlsId,
            @PathVariable String fileName) {

        // Validate video ID.
        try {
            UUID.fromString(hlsId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid video ID");
        }

        // Only allow the playlist and generated MPEG-TS segments.
        if (fileName == null
                || !(fileName.equals("master.m3u8")
                || fileName.matches("segment_\\d+\\.ts"))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid HLS file name");
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
