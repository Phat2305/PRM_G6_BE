package com.example.art_course.modules.order.entity;

import com.example.art_course.common.statemachine.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "supply_id")
    private UUID supplyId;

    @Column(name = "customize_request_id")
    private UUID customizeRequestId;

    @Column(name = "child_id")
    private UUID childId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "sub_total", nullable = false)
    private BigDecimal subTotal;

    @Column(name = "commission_rate", nullable = false)
    @Builder.Default
    private BigDecimal commissionRate = BigDecimal.ZERO;

    @Column(name = "commission_amount", nullable = false)
    @Builder.Default
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(name = "shipping_provider")
    private String shippingProvider;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "confirmed_delivered_at")
    private Instant confirmedDeliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Loại dòng suy ra từ cột khóa ngoại (đúng một trong ba cột có giá trị, ràng buộc bởi
     * chk_order_detail_type_exclusive): COURSE, SUPPLY hoặc CUSTOMIZE. Không lưu trong DB.
     */
    public String getProductType() {
        if (courseId != null) return "COURSE";
        if (supplyId != null) return "SUPPLY";
        if (customizeRequestId != null) return "CUSTOMIZE";
        return null;
    }
}
