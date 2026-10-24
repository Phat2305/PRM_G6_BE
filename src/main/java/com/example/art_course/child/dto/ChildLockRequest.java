package com.example.art_course.child.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChildLockRequest {

    @NotNull
    private Boolean locked;
}
