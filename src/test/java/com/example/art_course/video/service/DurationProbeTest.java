package com.example.art_course.video.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Đọc thời lượng video từ đầu ra của ffmpeg -i")
class DurationProbeTest {

    @Test
    void readsHoursMinutesSecondsAndRounds() {
        String info = "Input #0, mov,mp4, from 'in.mp4':\n"
                + "  Duration: 00:01:23.45, start: 0.000000, bitrate: 1205 kb/s\n"
                + "  Stream #0:0: Video: h264";
        assertEquals(83, VideoProcessingService.parseDurationSeconds(info));
        assertEquals(84, VideoProcessingService.parseDurationSeconds("  Duration: 00:01:23.60, start: 0"));
        assertEquals(3723, VideoProcessingService.parseDurationSeconds("Duration: 01:02:03.00, start"));
        assertEquals(10, VideoProcessingService.parseDurationSeconds("Duration: 00:00:10, start"));
    }

    @Test
    void unknownOrEmptyDurationIsNull() {
        assertNull(VideoProcessingService.parseDurationSeconds("Duration: N/A, bitrate: N/A"));
        assertNull(VideoProcessingService.parseDurationSeconds("Duration: 00:00:00.00, start: 0"));
        assertNull(VideoProcessingService.parseDurationSeconds("no duration here"));
        assertNull(VideoProcessingService.parseDurationSeconds(null));
    }
}
