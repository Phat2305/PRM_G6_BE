package com.example.art_course.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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

    @Size(max = 255, message = "Tài khoản ngân hàng tối đa 255 ký tự")
    private String bankAccountInfo;

    // Chỉ giáo viên; chuỗi rỗng = xóa
    @Pattern(regexp = "^$|^(0|\\+84)\\d{9}$", message = "Số điện thoại gồm 10 số, bắt đầu bằng 0 hoặc +84")
    private String phone;

    @Size(max = 500, message = "Giới thiệu tối đa 500 ký tự")
    private String bio;

    // Danh sách rỗng = xóa hết; null = giữ nguyên
    @Size(max = 10, message = "Tối đa 10 chứng chỉ")
    private List<@Size(min = 1, max = 150, message = "Mỗi chứng chỉ gồm 1-150 ký tự") String> certificates;
}
