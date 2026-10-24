package com.example.art_course.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Trường null hoặc rỗng = giữ nguyên
@Getter
@Setter
public class UpdateStaffRequest {
    @Size(max = 255, message = "Username tối đa 255 ký tự")
    private String username;

    @Size(min = 6, max = 20, message = "Password gồm 6-20 ký tự")
    private String password;
}
