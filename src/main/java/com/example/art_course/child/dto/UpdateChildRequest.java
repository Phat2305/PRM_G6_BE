package com.example.art_course.child.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateChildRequest {

    @Size(max = 255)
    private String displayName;

    @Min(1900)
    @Max(2100)
    private Short birthYear;
}