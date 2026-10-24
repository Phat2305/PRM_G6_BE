package com.example.art_course.child.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResetChildPasswordRequest {

    @NotBlank
    @Size(min = 6, max = 20, message = "Password must be 6-20 characters")
    private String newPassword;
}
