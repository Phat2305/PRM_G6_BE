
package com.example.art_course.child.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "child_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Child {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "birth_year")
    private Short birthYear;
}
