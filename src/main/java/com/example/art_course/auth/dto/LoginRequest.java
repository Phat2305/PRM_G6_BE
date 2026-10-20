package com.example.art_course.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Username là bắt buộc")
    private String username;

    @NotBlank(message = "Password là bắt buộc")
    private String password;
}
