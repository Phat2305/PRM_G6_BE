package com.example.art_course.modules.moderation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationReviewRequest {

    @NotBlank(message = "Loại đối tượng kiểm duyệt không được để trống (TEACHER_PROFILE, COURSE, SUPPLY, CUSTOMIZE_REQUEST, REFUND)")
    @JsonAlias({"targetType", "target_type", "type"})
    private String targetType;

    @NotNull(message = "Mã đối tượng kiểm duyệt không được để trống")
    @JsonAlias({"targetId", "target_id", "id"})
    private UUID targetId;

    @NotBlank(message = "Quyết định kiểm duyệt không được để trống (APPROVED, REJECTED, FLAGGED, RESOLVED)")
    @JsonAlias({"decision", "status", "action"})
    private String decision;

    @JsonAlias({"reason", "note", "comment"})
    private String reason;
}
