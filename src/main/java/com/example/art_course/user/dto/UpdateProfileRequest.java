package com.example.art_course.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Trường null = giữ nguyên giá trị cũ
@Getter
@Setter
public class UpdateProfileRequest {
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @Size(min = 1, max = 255, message = "Họ tên gồm 1-255 ký tự")
    private String fullName;

    private String shippingAddress;

    private String bankAccountInfo;
}
