package com.example.art_course.modules.fulfillment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherConfirmDeliveryRequest {

    @NotBlank(message = "Hành động không được để trống (CONFIRM hoặc REJECT)")
    private String action; // CONFIRM, REJECT

    private String reason;
}
