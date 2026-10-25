package com.example.art_course.admin.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectTeacherRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    @JsonAlias({"reason", "rejectReason", "note"})
    private String reason;
}
