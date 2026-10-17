
        package com.example.art_course.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "child_enroll")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment {

    @EmbeddedId
    private EnrollmentId id;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "active";

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = "active";
        }
    }
}
