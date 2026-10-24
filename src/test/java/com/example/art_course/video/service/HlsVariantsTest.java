package com.example.art_course.video.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HLS nhiều mức 360p/720p")
class HlsVariantsTest {

    @Test
    @DisplayName("Tên file: nhận cả dạng một mức cũ lẫn nhiều mức mới, chặn đường dẫn lạ")
    void fileNamePatterns() {
        for (String ok : List.of("master.m3u8", "stream_360.m3u8", "stream_720.m3u8")) {
            assertTrue(ok.matches(VideoProcessingService.PLAYLIST_PATTERN), ok);
        }
        for (String ok : List.of("segment_000.ts", "segment_360_000.ts", "segment_720_123.ts")) {
            assertTrue(ok.matches(VideoProcessingService.SEGMENT_PATTERN), ok);
        }
        for (String bad : List.of("../master.m3u8", "stream_a.m3u8", "ffmpeg.log", "segment_.ts", "x.ts")) {
            assertFalse(bad.matches(VideoProcessingService.PLAYLIST_PATTERN)
                    || bad.matches(VideoProcessingService.SEGMENT_PATTERN), bad);
        }
    }

    @Test
    @DisplayName("Lệnh ffmpeg: tách 360/720, var_stream_map khớp số luồng tiếng")
    void commandWithAndWithoutAudio() {
        Path out = Path.of("out");
        List<String> withAudio = VideoProcessingService.buildHlsCommand("ffmpeg", Path.of("in.mp4"), out, true);
        assertEquals("[0:v]split=2[s0][s1];[s0]scale=-2:'min(360,ih)'[v360];[s1]scale=-2:'min(720,ih)'[v720]",
                after(withAudio, "-filter_complex"));
        assertEquals("v:0,a:0,name:360 v:1,a:1,name:720", after(withAudio, "-var_stream_map"));
        assertEquals("master.m3u8", after(withAudio, "-master_pl_name"));
        assertTrue(withAudio.contains("0:a:0"));
        assertEquals(out.resolve("stream_%v.m3u8").toString(), withAudio.get(withAudio.size() - 1));

        List<String> silent = VideoProcessingService.buildHlsCommand("ffmpeg", Path.of("in.mp4"), out, false);
        assertEquals("v:0,name:360 v:1,name:720", after(silent, "-var_stream_map"));
        assertFalse(silent.contains("0:a:0"));
        assertFalse(silent.contains("-c:a"));
    }

    private static String after(List<String> cmd, String flag) {
        return cmd.get(cmd.indexOf(flag) + 1);
    }
}
