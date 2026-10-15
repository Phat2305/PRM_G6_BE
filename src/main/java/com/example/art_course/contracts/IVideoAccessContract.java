package com.example.art_course.contracts;

import java.util.Optional;
import java.util.UUID;

/**
 * Hợp đồng giao tiếp giữa BE2 và BE1 về cấp quyền truy cập video (LRN-02, LRN-03).
 * BE2 tuyệt đối không tự phát URL video thô từ cơ sở dữ liệu.
 */
public interface IVideoAccessContract {

    /**
     * Yêu cầu BE1 sinh Signed URL có thời hạn cho bài học xem thử miễn phí (SHOP-10).
     * @param lessonId ID bài học xem thử
     * @return Signed URL hoặc Optional.empty() nếu BE1 chưa sẵn sàng dịch vụ video
     */
    Optional<String> getSignedTrialVideoUrl(UUID lessonId);
}
