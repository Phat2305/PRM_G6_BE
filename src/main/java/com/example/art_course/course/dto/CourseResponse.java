
package com.example.art_course.course.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CourseResponse {

    private UUID id;
    private UUID teacherId;
    private UUID customizeRequestId;
    private String name;
    private String description;
    private String category;
    private String coverImageUrl;
    private BigDecimal price;
    private String status;
    // Số lần Staff đã từ chối (tối đa 3 thì không gửi lại được) và lý do gần nhất.
    private int rejectionCount;
    private String reviewNote;
}
