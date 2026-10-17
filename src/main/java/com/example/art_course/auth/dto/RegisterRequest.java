package com.example.art_course.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @JsonAlias({"username", "user_name", "email", "phone"})
    private String username;

    @JsonAlias({"password", "pass"})
    private String password;

    @JsonAlias({"role", "user_role", "userRole"})
    private String role;
}
