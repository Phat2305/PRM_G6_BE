package com.example.art_course.modules.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity(name = "Be2Supply")
@Table(name = "supplies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supply {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    // Họ tên giáo viên, được điền khi trả API (không lưu trong bảng supplies)
    @Transient
    private String teacherName;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Column(nullable = false, length = 20)
    private String status;

    // Hết hàng thì tự chuyển OUT_OF_STOCK; có hàng lại (giáo viên nhập thêm, hủy/hoàn đơn) thì tự về ACTIVE, không cần duyệt lại.
    // Chỉ đụng hai trạng thái này: PENDING_REVIEW, REJECTED, HIDDEN do kiểm duyệt quyết định.
    public void syncStatusWithStock() {
        if (stockQuantity <= 0 && "ACTIVE".equalsIgnoreCase(status)) {
            status = "OUT_OF_STOCK";
        } else if (stockQuantity > 0 && "OUT_OF_STOCK".equalsIgnoreCase(status)) {
            status = "ACTIVE";
        }
    }

    public int getAvailableStock() {
        return Math.max(0, stockQuantity - reservedQuantity);
    }
}
