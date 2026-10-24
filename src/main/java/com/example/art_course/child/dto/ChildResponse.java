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

    // Parent locked this child (users.status = LOCKED); the child cannot log in until unlocked.
    private boolean locked;

    public String getName() {
        return displayName;
    }
}