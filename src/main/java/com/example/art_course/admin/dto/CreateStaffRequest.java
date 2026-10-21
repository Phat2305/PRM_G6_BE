package com.example.art_course.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateStaffRequest {
    @NotBlank(message = "Username là bắt buộc")
    @Size(max = 255, message = "Username tối đa 255 ký tự")
    private String username;

    @NotBlank(message = "Password là bắt buộc")
    @Size(min = 8, max = 72, message = "Password gồm 8-72 ký tự")
    private String password;
}
