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

    // Rỗng hoặc null nghĩa là giữ mật khẩu cũ; AdminUserService kiểm 6-20 ký tự khi có giá trị.
    private String password;
}
