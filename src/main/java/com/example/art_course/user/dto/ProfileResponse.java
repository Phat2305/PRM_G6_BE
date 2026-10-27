package com.example.art_course.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

// shippingAddress chỉ có ở Parent; bankAccountInfo, bio, certificates, shippingFee chỉ có ở Teacher; phone cả hai
@Getter
@AllArgsConstructor
public class ProfileResponse {
    private UUID id;
    private String username;
    private String email;
    private String role;
    private String status;
    private String fullName;
    private String shippingAddress;
    private String bankAccountInfo;
    private String phone;
    private String bio;
    private List<String> certificates;
    private Long shippingFee;
    // Chỉ giáo viên: certificateFiles[i] = chứng chỉ thứ i đã đính kèm file
    private List<Boolean> certificateFiles;
}
