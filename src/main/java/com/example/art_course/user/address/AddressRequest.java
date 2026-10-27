package com.example.art_course.user.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressRequest {

    @NotBlank(message = "Chưa nhập tên người nhận")
    @Size(max = 255, message = "Tên người nhận tối đa 255 ký tự")
    private String recipientName;

    @NotBlank(message = "Chưa nhập số điện thoại")
    @Pattern(regexp = "^(0|\\+84)\\d{9}$", message = "Số điện thoại gồm 10 số, bắt đầu bằng 0 hoặc +84")
    private String phone;

    @NotBlank(message = "Chưa nhập địa chỉ")
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String addressLine;

    // null = giữ nguyên khi sửa; khi thêm, địa chỉ đầu tiên luôn là mặc định.
    private Boolean isDefault;
}
