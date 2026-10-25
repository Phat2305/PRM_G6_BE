
package com.example.art_course.video.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class VideoProcessingService {

    // Tên file HLS được phép lưu/phát. Video cũ: master.m3u8 + segment_000.ts (một mức);
    // video mới: master.m3u8 liệt kê stream_360.m3u8, stream_720.m3u8 + segment_360_000.ts...
    public static final String PLAYLIST_PATTERN = "master\\.m3u8|stream_\\d+\\.m3u8";
    public static final String SEGMENT_PATTERN = "segment_(\\d+_)?\\d+\\.ts";

    // Chiều cao các mức xuất ra; nguồn thấp hơn thì giữ nguyên (không phóng to).
    static final int[] HEIGHTS = {360, 720};

    // "Duration: 00:01:23.45," in the output of `ffmpeg -i`.
    private static final Pattern DURATION =
            Pattern.compile("Duration:\\s*(\\d+):(\\d{2}):(\\d{2})(?:\\.(\\d+))?");

    // hlsId is the folder under hls/ on R2; durationSeconds is null when ffmpeg could not tell the length.
    public record HlsResult(String hlsId, Integer durationSeconds) {}

    record Probe(boolean hasAudio, Integer durationSeconds) {}

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
        return convert(inputPath).hlsId();
    }

    public HlsResult convert(String inputPath) {
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
            Path logFile = outputDirectory.resolve("ffmpeg.log");

            Probe probe = probe(input);

            ProcessBuilder builder = new ProcessBuilder(
                    buildHlsCommand(ffmpegPath, input, outputDirectory, probe.hasAudio()));

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

            // Segment trước, playlist sau: playlist chỉ trỏ tới file đã có trên R2.
            try (Stream<Path> files = Files.list(outputDirectory)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName()
                                .toString()
                                .matches(SEGMENT_PATTERN))
                        .forEach(path -> r2StorageService.uploadFile(
                                objectPrefix + path.getFileName(),
                                path,
                                "video/mp2t"));
            }
            try (Stream<Path> files = Files.list(outputDirectory)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName()
                                .toString()
                                .matches(PLAYLIST_PATTERN))
                        .forEach(path -> r2StorageService.uploadFile(
                                objectPrefix + path.getFileName(),
                                path,
                                "application/vnd.apple.mpegurl"));
            }

            return new HlsResult(outputId, probe.durationSeconds());

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
                || !content.contains("#EXT-X-STREAM-INF")) {
            return false;
        }

        // Mọi playlist con phải có segment, và phải có ít nhất một segment không rỗng.
        for (int height : HEIGHTS) {
            Path stream = outputDirectory.resolve("stream_" + height + ".m3u8");
            if (!Files.isRegularFile(stream)
                    || !Files.readString(stream).contains("#EXTINF:")) {
                return false;
            }
        }

        try (Stream<Path> files = Files.list(outputDirectory)) {
            return files.anyMatch(path ->
                    Files.isRegularFile(path)
                            && path.getFileName().toString()
                            .matches(SEGMENT_PATTERN)
                            && sizeIsPositive(path));
        }
    }

    // Một lần chạy ffmpeg xuất 360p + 720p (nguồn thấp hơn giữ nguyên chiều cao) và master.m3u8 liệt kê cả hai.
    static List<String> buildHlsCommand(
            String ffmpegPath, Path input, Path outputDirectory, boolean audio) {

        StringBuilder filter = new StringBuilder("[0:v]split=" + HEIGHTS.length);
        for (int i = 0; i < HEIGHTS.length; i++) {
            filter.append("[s").append(i).append(']');
        }
        StringBuilder streamMap = new StringBuilder();
        List<String> maps = new ArrayList<>();
        for (int i = 0; i < HEIGHTS.length; i++) {
            int h = HEIGHTS[i];
            filter.append(";[s").append(i).append("]scale=-2:'min(").append(h)
                    .append(",ih)'[v").append(h).append(']');
            maps.add("-map");
            maps.add("[v" + h + "]");
            if (audio) {
                maps.add("-map");
                maps.add("0:a:0");
            }
            if (i > 0) streamMap.append(' ');
            streamMap.append("v:").append(i);
            if (audio) streamMap.append(",a:").append(i);
            streamMap.append(",name:").append(h);
        }

        List<String> cmd = new ArrayList<>(List.of(
                ffmpegPath, "-y",
                "-i", input.toString(),
                "-filter_complex", filter.toString()));
        cmd.addAll(maps);
        cmd.addAll(List.of(
                "-c:v", "libx264",
                "-preset", "veryfast"));
        if (audio) cmd.addAll(List.of("-c:a", "aac"));
        cmd.addAll(List.of(
                "-f", "hls",
                "-hls_time", "6",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename",
                outputDirectory.resolve("segment_%v_%03d.ts").toString(),
                "-master_pl_name", "master.m3u8",
                "-var_stream_map", streamMap.toString(),
                outputDirectory.resolve("stream_%v.m3u8").toString()));
        return cmd;
    }

    // var_stream_map phải khớp đúng số luồng tiếng; video không tiếng mà map a:0 thì ffmpeg báo lỗi.
    // Cùng một lần đọc thông tin lấy luôn thời lượng (dùng để kiểm "đã học xong" ở child_progress).
    private Probe probe(Path input) {
        try {
            Process probe = new ProcessBuilder(ffmpegPath, "-hide_banner", "-i", input.toString())
                    .redirectErrorStream(true)
                    .start();
            String info = new String(probe.getInputStream().readAllBytes());
            probe.waitFor(1, TimeUnit.MINUTES);
            return new Probe(info.contains("Audio:"), parseDurationSeconds(info));
        } catch (IOException e) {
            return new Probe(true, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Probe(true, null);
        }
    }

    // Whole seconds, rounded; null when the output has no usable "Duration:" (for example "N/A" or 0).
    static Integer parseDurationSeconds(String ffmpegInfo) {
        Matcher m = DURATION.matcher(ffmpegInfo == null ? "" : ffmpegInfo);
        if (!m.find()) {
            return null;
        }
        double seconds = Integer.parseInt(m.group(1)) * 3600.0
                + Integer.parseInt(m.group(2)) * 60.0
                + Integer.parseInt(m.group(3))
                + (m.group(4) == null ? 0 : Double.parseDouble("0." + m.group(4)));
        long rounded = Math.round(seconds);
        return rounded > 0 && rounded <= Integer.MAX_VALUE ? (int) rounded : null;
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
