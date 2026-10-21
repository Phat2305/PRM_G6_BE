
package com.example.art_course.video.dto;

import java.io.Serializable;

public class VideoProcessingMessage implements Serializable {

    private String videoId;
    private String inputPath;

    public VideoProcessingMessage() {
    }

    public VideoProcessingMessage(String videoId, String inputPath) {
        this.videoId = videoId;
        this.inputPath = inputPath;
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
}
