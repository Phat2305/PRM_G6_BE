package com.example.art_course.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffStatusRequest {
    @NotBlank(message = "status là bắt buộc (ACTIVE hoặc LOCKED)")
    private String status;
}
