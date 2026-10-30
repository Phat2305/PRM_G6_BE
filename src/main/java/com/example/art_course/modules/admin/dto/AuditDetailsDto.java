package com.example.art_course.modules.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// ADM-04/05: dữ liệu liên quan hiện tại để đọc nhật ký, không sửa bút toán lịch sử.
public record AuditDetailsDto(String title, String description, String teacherName, String parentName,
                              BigDecimal totalAmount, String status, UUID orderId, String orderStatus,
                              String bankInfo, List<Item> items, List<Proof> proofs) {
    public record Item(String name, int quantity, BigDecimal unitPrice, BigDecimal subTotal) {}
    public record Proof(UUID referenceId, String type, String url) {}
}
