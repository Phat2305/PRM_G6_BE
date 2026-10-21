package com.example.art_course.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

// shippingAddress chỉ có ở Parent, bankAccountInfo chỉ có ở Teacher
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
}
