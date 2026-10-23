
package com.example.art_course.video.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class VideoProcessingService {

    private final String ffmpegPath;
    private final Path hlsDirectory;
    private final R2StorageService r2StorageService;

    public VideoProcessingService(
            @Value("${app.video.ffmpeg-path}") String ffmpegPath,
            @Value("${app.video.hls-dir}") String hlsDir,
            R2StorageService r2StorageService) {

        this.ffmpegPath = ffmpegPath;
        this.hlsDirectory = Paths.get(hlsDir)
                .toAbsolutePath()
                .normalize();
        this.r2StorageService = r2StorageService;
    }

    public String convertToHls(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source video path is required");
        }

        Path input = Paths.get(inputPath)
                .toAbsolutePath()
                .normalize();

        if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Source video not found or unreadable");
        }

        String outputId = UUID.randomUUID().toString();

        Path outputDirectory = hlsDirectory.resolve(outputId).normalize();

        if (!outputDirectory.startsWith(hlsDirectory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid output path");
        }

        try {
            Files.createDirectories(hlsDirectory);
            Files.createDirectory(outputDirectory);

            Path playlist = outputDirectory.resolve("master.m3u8");
            Path segments = outputDirectory.resolve("segment_%03d.ts");
            Path logFile = outputDirectory.resolve("ffmpeg.log");

            ProcessBuilder builder = new ProcessBuilder(
                    ffmpegPath,
                    "-y",
                    "-i", input.toString(),
                    "-c:v", "libx264",
                    // Giảm RAM và CPU để không vượt giới hạn bộ nhớ của instance Render.
                    "-preset", "veryfast",
                    "-threads", "1",
                    // Tối đa 720p (video nhỏ hơn giữ nguyên) để giảm RAM của ffmpeg.
                    "-vf", "scale=-2:'min(720,ih)'",
                    "-c:a", "aac",
                    "-f", "hls",
                    "-hls_time", "6",
                    "-hls_playlist_type", "vod",
                    "-hls_segment_filename", segments.toString(),
                    playlist.toString()
            );

            builder.redirectErrorStream(true);
            builder.redirectOutput(logFile.toFile());

            Process process = builder.start();

            boolean completed;

            try {
                completed = process.waitFor(30, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();

                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Video processing was interrupted",
                        e);
            }

            if (!completed) {
                process.destroyForcibly();

                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "Video processing timed out");
            }

            if (process.exitValue() != 0
                    || !isValidHlsOutput(outputDirectory, playlist)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Video conversion failed. Check ffmpeg.log");
            }

            // Upload playlist and segments to R2.
            String objectPrefix = "hls/" + outputId + "/";

            r2StorageService.uploadFile(
                    objectPrefix + "master.m3u8",
                    playlist,
                    "application/vnd.apple.mpegurl");

            try (Stream<Path> files = Files.list(outputDirectory)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName()
                                .toString()
                                .matches("segment_\\d+\\.ts"))
                        .forEach(path -> r2StorageService.uploadFile(
                                objectPrefix + path.getFileName(),
                                path,
                                "video/mp2t"));
            }

            return outputId;

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot execute FFmpeg or access HLS files",
                    e);

        } finally {
            // HLS được phát từ R2 và job lỗi không requeue, nên file cục bộ không còn cần nữa.
            // shortcut: xóa cả khi lỗi (mất ffmpeg.log), cần giữ input khi thêm retry/DLQ.
            deleteDirectoryQuietly(outputDirectory);
            try {
                Files.deleteIfExists(input);
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private boolean isValidHlsOutput(
            Path outputDirectory,
            Path playlist) throws IOException {

        if (!Files.isRegularFile(playlist)
                || Files.size(playlist) == 0) {
            return false;
        }

        String content = Files.readString(playlist);

        if (!content.contains("#EXTM3U")
                || !content.contains("#EXTINF:")) {
            return false;
        }

        try (Stream<Path> files = Files.list(outputDirectory)) {
            return files.anyMatch(path ->
                    Files.isRegularFile(path)
                            && path.getFileName().toString()
                            .matches("segment_\\d+\\.ts")
                            && sizeIsPositive(path));
        }
    }

    private boolean sizeIsPositive(Path path) {
        try {
            return Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        try {
            if (!Files.exists(directory)) {
                return;
            }

            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                                // Best-effort cleanup.
                            }
                        });
            }
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}
