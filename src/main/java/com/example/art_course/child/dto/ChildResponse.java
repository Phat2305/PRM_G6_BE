package com.example.art_course.child.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChildResponse {

    private UUID userId;

    private String username;

    private String displayName;

    private Short birthYear;

    public String getName() {
        return displayName;
    }
}