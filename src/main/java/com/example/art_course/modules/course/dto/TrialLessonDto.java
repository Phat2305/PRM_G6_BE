package com.example.art_course.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO xem thử bài học miễn phí (SHOP-10).
 * Tuyệt đối không trả URL video thô. Video chỉ được truy cập qua signed URL có thời hạn từ BE1 (LRN-02/03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialLessonDto {
    private UUID lessonId;
    private UUID courseId;
    private String title;
    private boolean isTrial;
    private boolean hasAssignment;
    private String signedVideoUrl;
    private String message;
}
