package com.example.art_course.modules.fulfillment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherConfirmDeliveryRequest {

    @NotBlank(message = "Hành động không được để trống (CONFIRM hoặc REJECT)")
    @JsonAlias({"action", "status"})
    private String action; // CONFIRM, REJECT

    @JsonAlias({"reason", "note"})
    private String reason;
}
