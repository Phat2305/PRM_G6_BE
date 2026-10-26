package com.example.art_course.modules.staff.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

// Lý do Staff từ chối khóa học hoặc bài đăng (bắt buộc, gửi cho giáo viên/phụ huynh).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectReasonRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    @JsonAlias({"reason", "rejectReason", "note"})
    private String reason;
}
