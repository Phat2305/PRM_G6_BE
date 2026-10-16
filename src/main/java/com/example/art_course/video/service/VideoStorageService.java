
package com.example.art_course.video.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class VideoStorageService {

    private final Path uploadDirectory;
    private final long maxFileSize;

    public VideoStorageService(
            @Value("${app.video.upload-dir}") String uploadDir,
            @Value("${app.video.max-file-size:104857600}") long maxFileSize) {

        this.uploadDirectory = Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize();

        this.maxFileSize = maxFileSize;

        if (maxFileSize <= 0) {
            throw new IllegalArgumentException(
                    "Video max file size must be greater than zero");
        }
    }

    public StoredVideo store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Video file is required");
        }

        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Video file exceeds the maximum allowed size");
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null
                || !originalName.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only MP4 files are allowed");
        }

        try {
            Files.createDirectories(uploadDirectory);

            String fileName = UUID.randomUUID() + ".mp4";
            Path destination = uploadDirectory.resolve(fileName)
                    .normalize();

            if (!destination.startsWith(uploadDirectory)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid file path");
            }

            file.transferTo(destination);

            return new StoredVideo(
                    fileName,
                    destination.toString());

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot save video file",
                    e);
        }
    }

    public record StoredVideo(
            String fileName,
            String absolutePath) {
    }
}
