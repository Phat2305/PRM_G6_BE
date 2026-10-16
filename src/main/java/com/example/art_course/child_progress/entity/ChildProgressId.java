
        package com.example.art_course.child_progress.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChildProgressId implements Serializable {

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;
}
