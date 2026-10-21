package com.example.art_course.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank(message = "Username là bắt buộc")
    @Pattern(regexp = "^[A-Za-z0-9_.]{3,50}$",
            message = "Username gồm 3-50 ký tự chữ, số, dấu gạch dưới hoặc dấu chấm")
    private String username;

    @NotBlank(message = "Số điện thoại là bắt buộc")
    @Pattern(regexp = "^(0|\\+84)\\d{9}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @NotBlank(message = "Họ tên là bắt buộc")
    @Size(max = 255, message = "Họ tên tối đa 255 ký tự")
    private String fullName;

    @NotBlank(message = "Password là bắt buộc")
    @Size(min = 6, max = 12, message = "Password gồm 6-12 ký tự")
    private String password;

    @NotNull(message = "Role là bắt buộc (PARENT hoặc TEACHER)")
    private RegisterRole role;
}
