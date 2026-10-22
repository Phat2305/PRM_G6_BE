
package com.example.art_course.video.dto;

import java.io.Serializable;

public class VideoProcessingMessage implements Serializable {

    private String videoId;
    private String inputPath;
    private String lessonId;

    public VideoProcessingMessage() {
    }

    // Dùng cho upload video thông thường
    public VideoProcessingMessage(String videoId, String inputPath) {
        this.videoId = videoId;
        this.inputPath = inputPath;
    }

    // Dùng cho upload video gắn với lesson
    public VideoProcessingMessage(
            String videoId,
            String inputPath,
            String lessonId
    ) {
        this.videoId = videoId;
        this.inputPath = inputPath;
        this.lessonId = lessonId;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getInputPath() {
        return inputPath;
    }

    public void setInputPath(String inputPath) {
        this.inputPath = inputPath;
    }

    public String getLessonId() {
        return lessonId;
    }

    public void setLessonId(String lessonId) {
        this.lessonId = lessonId;
    }
}
