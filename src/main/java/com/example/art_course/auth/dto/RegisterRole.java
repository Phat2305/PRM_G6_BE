package com.example.art_course.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

// Role tự đăng ký được; status ban đầu đi kèm role (TEACHER chờ duyệt).
public enum RegisterRole {
    PARENT("ACTIVE"),
    TEACHER("PENDING");

    private final String initialStatus;

    RegisterRole(String initialStatus) {
        this.initialStatus = initialStatus;
    }

    public String initialStatus() {
        return initialStatus;
    }

    // Không phân biệt hoa/thường; giá trị lạ -> IllegalArgumentException -> 400
    @JsonCreator
    public static RegisterRole from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
