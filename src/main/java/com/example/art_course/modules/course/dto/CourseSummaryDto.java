package com.example.art_course.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO tóm tắt khóa học phục vụ duyệt danh mục mua sắm (SHOP-01).
 * Tuyệt đối không chứa trường videoUrl.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSummaryDto {
    private UUID id;
    private UUID teacherId;
    // Họ tên giáo viên (teacher_profiles.full_name)
    private String teacherName;
    private String name;
    private String description;
    private String category;
    private String coverImageUrl;
    private BigDecimal price;
    private String status;
    // Điểm trung bình (làm tròn 1 chữ số) và số đánh giá; chưa có đánh giá thì null và 0.
    private Double averageRating;
    private long reviewCount;
}
