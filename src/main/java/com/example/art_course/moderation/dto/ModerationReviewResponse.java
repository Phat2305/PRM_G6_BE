
        package com.example.art_course.moderation.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationReviewResponse {

    private UUID id;
    private UUID staffId;
    private String targetType;
    private UUID targetId;
    private String decision;
    private String reason;
    private OffsetDateTime createdAt;
}
