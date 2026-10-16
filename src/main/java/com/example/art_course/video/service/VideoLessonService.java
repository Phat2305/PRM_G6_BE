
package com.example.art_course.video.service;

import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class VideoLessonService {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final VideoStorageService videoStorageService;
    private final VideoProcessingService videoProcessingService;

    public VideoLessonService(
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            VideoStorageService videoStorageService,
            VideoProcessingService videoProcessingService) {

        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.videoStorageService = videoStorageService;
        this.videoProcessingService = videoProcessingService;
    }

    @Transactional
    public String uploadLessonVideo(
            UUID lessonId,
            MultipartFile file,
            String username) {

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

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lesson not found"));

        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Course not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));

        if (course.getTeacherId() == null
                || !course.getTeacherId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to upload video to this lesson");
        }

        VideoStorageService.StoredVideo storedVideo =
                videoStorageService.store(file);

        String hlsId = videoProcessingService.convertToHls(
                storedVideo.absolutePath());

        String playlistUrl = "/api/videos/hls/"
                + hlsId + "/master.m3u8";

        lesson.setVideoUrl(playlistUrl);
        lessonRepository.save(lesson);

        return playlistUrl;
    }
}
