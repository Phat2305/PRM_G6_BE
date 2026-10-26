
        package com.example.art_course.customize_request.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomizeRequestResponse {

    private UUID id;
    private UUID parentId;
    private String title;
    private String description;
    private String category;
    private Short childAge;
    private BigDecimal budget;
    private OffsetDateTime deadline;
    private String status;
    // Số lần Staff đã từ chối (tối đa 3 thì bài bị từ chối hẳn) và lý do gần nhất.
    private int rejectionCount;
    private String reviewNote;
}
