
package com.example.art_course.video.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/videos/hls")
public class VideoHlsController {

    private final Path hlsDirectory;

    public VideoHlsController(
            @Value("${app.video.hls-dir}") String hlsDir) {
        this.hlsDirectory = Paths.get(hlsDir)
                .toAbsolutePath()
                .normalize();
    }

    @GetMapping("/{hlsId}/{fileName}")
    public ResponseEntity<Resource> getHlsFile(
            @PathVariable String hlsId,
            @PathVariable String fileName) {

        // Only allow UUID-based HLS directories.
        if (hlsId == null
                || !hlsId.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid video ID");
        }

        // Only serve playlists and generated MPEG-TS segments.
        if (fileName == null
                || !(fileName.equals("master.m3u8")
                || fileName.matches("segment_\\d+\\.ts"))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid HLS file name");
        }

        Path videoDirectory = hlsDirectory.resolve(hlsId).normalize();
        Path file = videoDirectory.resolve(fileName).normalize();

        if (!videoDirectory.startsWith(hlsDirectory)
                || !file.startsWith(videoDirectory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid file path");
        }

        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "HLS file not found");
        }

        try {
            MediaType mediaType = fileName.endsWith(".m3u8")
                    ? MediaType.parseMediaType("application/vnd.apple.mpegurl")
                    : MediaType.parseMediaType("video/mp2t");

            Resource resource = new FileSystemResource(file);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CACHE_CONTROL,
                            "private, no-cache")
                    .body(resource);

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to serve HLS file",
                    e);
        }
    }
}
