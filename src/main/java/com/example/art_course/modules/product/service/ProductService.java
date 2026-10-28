package com.example.art_course.modules.product.service;

import com.example.art_course.common.AppError;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.repository.SupplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final SupplyRepository supplyRepository;
    private final com.example.art_course.common.TeacherNames teacherNames;

    @Transactional(readOnly = true)
    public List<Supply> listSupplies() {
        return listSupplies(null, null, null).items();
    }

    /**
     * Danh mục dụng cụ có lọc và phân trang. q khớp tên dụng cụ hoặc tên giáo viên; size == null thì trả cả danh sách.
     * Sắp xếp theo tên rồi id để các trang không trùng hoặc sót mục.
     */
    @Transactional(readOnly = true)
    public com.example.art_course.common.PagedResult<Supply> listSupplies(String q, Integer page, Integer size) {
        List<Supply> all = supplyRepository.findByStatusInIgnoreCase(List.of("ACTIVE", "OUT_OF_STOCK")); // hết hàng vẫn hiện để FE ghi "Hết hàng"
        java.util.Map<UUID, String> names = teacherNames.byIds(
                all.stream().map(Supply::getTeacherId).distinct().toList());
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<Supply> matched = all.stream()
                .filter(s -> needle.isEmpty()
                        || contains(s.getName(), needle)
                        || contains(names.get(s.getTeacherId()), needle))
                .sorted(java.util.Comparator
                        .comparing((Supply s) -> s.getName() == null ? "" : s.getName().toLowerCase())
                        .thenComparing(s -> String.valueOf(s.getId())))
                .toList();
        var paged = com.example.art_course.common.PagedResult.of(matched, page, size);
        // Phí ship chỉ tra cho các mục trong trang này.
        java.util.Map<UUID, Long> fees = new java.util.HashMap<>();
        paged.items().forEach(s -> {
            s.setTeacherName(names.get(s.getTeacherId()));
            s.setShippingFee(fees.computeIfAbsent(s.getTeacherId(), teacherNames::shippingFeeOf));
        });
        return paged;
    }

    private static boolean contains(String text, String lowerNeedle) {
        return text != null && text.toLowerCase().contains(lowerNeedle);
    }

    // Endpoint công khai: giống danh sách, chỉ dụng cụ đang bán (hoặc hết hàng). Bản chờ duyệt/bị ẩn coi như không tồn tại.
    @Transactional(readOnly = true)
    public Supply getSupplyDetail(UUID supplyId) {
        Supply supply = supplyRepository.findById(supplyId)
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus()) || "OUT_OF_STOCK".equalsIgnoreCase(s.getStatus()))
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "SUPPLY_NOT_FOUND", "Không tìm thấy dụng cụ vẽ"));
        supply.setTeacherName(teacherNames.byId(supply.getTeacherId()));
        supply.setShippingFee(teacherNames.shippingFeeOf(supply.getTeacherId()));
        return supply;
    }
}
