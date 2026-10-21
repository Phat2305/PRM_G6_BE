package com.example.art_course.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

// Không trả entity User để không lộ hash mật khẩu
@Getter
@AllArgsConstructor
public class StaffResponse {
    private UUID id;
    private String username;
    private String status;
}
