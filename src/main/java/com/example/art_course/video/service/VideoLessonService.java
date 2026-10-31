
package com.example.art_course.video.service;

import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.course.service.CourseReviewPolicy;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import com.example.art_course.video.dto.VideoProcessingMessage;
import com.example.art_course.video.messaging.VideoProcessingPublisher;

import com.example.art_course.common.AppError;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VideoLessonService {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final VideoStorageService videoStorageService;
    private final VideoProcessingPublisher videoProcessingPublisher;
    private final R2StorageService r2StorageService;
    private final com.example.art_course.course.service.CourseService courseService;

    public VideoLessonService(
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            VideoStorageService videoStorageService,
            VideoProcessingPublisher videoProcessingPublisher,
            R2StorageService r2StorageService,
            com.example.art_course.course.service.CourseService courseService) {

        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.videoStorageService = videoStorageService;
        this.videoProcessingPublisher = videoProcessingPublisher;
        this.r2StorageService = r2StorageService;
        this.courseService = courseService;
    }

    @Transactional
    public String queueLessonVideo(
            UUID lessonId,
            MultipartFile file,
            String userId) {

        if (lessonId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lesson ID is required");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Video file is required");
        }

        Lesson lesson = ownedLesson(lessonId, userId);
        // Đổi video là sửa nội dung khóa: khóa đang công khai quay về chờ Staff duyệt lại.
        courseRepository.findById(lesson.getCourseId()).ifPresent(course -> {
            courseService.requireEditable(course);
            courseService.prepareCustomizeEdit(course);
            String before = course.getStatus();
            CourseReviewPolicy.onContentChanged(course);
            if (before == null ? course.getStatus() != null : !before.equals(course.getStatus())) {
                courseRepository.save(course);
            }
        });

        VideoStorageService.StoredVideo storedVideo =
                videoStorageService.store(file);

        String videoId = UUID.randomUUID().toString();

        // Ghi QUEUED trước khi publish; publish lỗi thì transaction rollback.
        // Flush ngay để giữ khóa dòng: worker ghi PROCESSING phải chờ commit này, không bị QUEUED đè.
        lesson.setVideoStatus("QUEUED");
        lesson.setVideoError(null);
        lessonRepository.saveAndFlush(lesson);

        VideoProcessingMessage message = new VideoProcessingMessage(
                videoId,
                storedVideo.absolutePath(),
                lessonId.toString());

        videoProcessingPublisher.publish(message);

        return videoId;
    }

    // Gỡ video của bài học (giáo viên của khóa): xóa thư mục HLS trên R2 rồi xóa dấu vết trong DB.
    // Đang xếp hàng/xử lý thì từ chối vì worker sẽ ghi đè kết quả sau đó.
    @Transactional
    public void deleteLessonVideo(UUID lessonId, String userId) {
        Lesson lesson = ownedLesson(lessonId, userId);
        courseRepository.findById(lesson.getCourseId()).ifPresent(course -> {
            courseService.requireEditable(course);
            courseService.prepareCustomizeEdit(course);
        });
        String status = lesson.getVideoStatus();
        if ("QUEUED".equals(status) || "PROCESSING".equals(status)) {
            throw new AppError(HttpStatus.CONFLICT, "VIDEO_PROCESSING",
                    "Video đang được xử lý, thử lại sau khi xử lý xong");
        }
        String url = lesson.getVideoUrl();
        String hlsId = hlsIdOf(url);
        if (hlsId != null) {
            r2StorageService.deletePrefix("hls/" + hlsId + "/");
        }
        lesson.setVideoUrl(null);
        lesson.setVideoStatus(null);
        lesson.setVideoError(null);
        lesson.setDurationSeconds(null);
        lessonRepository.save(lesson);
        courseRepository.findById(lesson.getCourseId()).ifPresent(course -> {
            String before = course.getStatus();
            CourseReviewPolicy.onContentChanged(course);
            if (before == null ? course.getStatus() != null : !before.equals(course.getStatus())) {
                courseRepository.save(course);
            }
        });
    }

    // videoUrl có dạng /api/videos/hls/{hlsId}/master.m3u8; không đúng dạng thì không có gì để xóa trên R2.
    public static String hlsIdOf(String videoUrl) {
        String prefix = "/api/videos/hls/";
        if (videoUrl == null || !videoUrl.startsWith(prefix)) {
            return null;
        }
        String rest = videoUrl.substring(prefix.length());
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        return id.isBlank() || id.contains("..") ? null : id;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLessonVideoStatus(UUID lessonId, String userId) {
        Lesson lesson = ownedLesson(lessonId, userId);
        String status = lesson.getVideoStatus();
        if (status == null) {
            // Bài học có từ trước khi có cột video_status.
            status = lesson.getVideoUrl() == null || lesson.getVideoUrl().isBlank()
                    ? "NO_VIDEO" : "READY";
        }
        // HashMap vì error/videoUrl có thể null.
        Map<String, Object> body = new HashMap<>();
        body.put("lessonId", lessonId.toString());
        body.put("status", status);
        body.put("error", lesson.getVideoError());
        body.put("videoUrl", lesson.getVideoUrl());
        return body;
    }

    @Transactional
    public void markProcessing(UUID lessonId) {
        lessonRepository.findById(lessonId).ifPresent(lesson -> {
            lesson.setVideoStatus("PROCESSING");
            lesson.setVideoError(null);
            lessonRepository.save(lesson);
        });
    }

    @Transactional
    public void markFailed(UUID lessonId, Exception error) {
        String message = error instanceof ResponseStatusException rse
                && rse.getReason() != null
                ? rse.getReason()
                : "Video processing failed";
        lessonRepository.findById(lessonId).ifPresent(lesson -> {
            lesson.setVideoStatus("FAILED");
            lesson.setVideoError(message.length() > 500
                    ? message.substring(0, 500) : message);
            lessonRepository.save(lesson);
        });
    }

    private Lesson ownedLesson(UUID lessonId, String userId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lesson not found"));

        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Course not found"));

        UUID parsedUserId;

        try {
            parsedUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authenticated user ID");
        }

        User user = userRepository.findById(parsedUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));

        if (course.getTeacherId() == null
                || !course.getTeacherId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to access video of this lesson");
        }

        return lesson;
    }

    @Transactional
    public void completeLessonVideo(UUID lessonId, String hlsId, Integer durationSeconds) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lesson not found"));

        lesson.setVideoUrl("/api/videos/hls/" + hlsId + "/master.m3u8");
        lesson.setVideoStatus("READY");
        lesson.setVideoError(null);
        lesson.setDurationSeconds(durationSeconds);
        lessonRepository.save(lesson);
    }
}
