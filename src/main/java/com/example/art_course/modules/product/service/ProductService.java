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
        List<Supply> supplies = supplyRepository.findByStatusInIgnoreCase(List.of("ACTIVE", "OUT_OF_STOCK")); // hết hàng vẫn hiện để FE ghi "Hết hàng"
        java.util.Map<UUID, String> names = teacherNames.byIds(
                supplies.stream().map(Supply::getTeacherId).distinct().toList());
        supplies.forEach(s -> s.setTeacherName(names.get(s.getTeacherId())));
        return supplies;
    }

    @Transactional(readOnly = true)
    public Supply getSupplyDetail(UUID supplyId) {
        Supply supply = supplyRepository.findById(supplyId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "SUPPLY_NOT_FOUND", "Không tìm thấy dụng cụ vẽ"));
        supply.setTeacherName(teacherNames.byId(supply.getTeacherId()));
        return supply;
    }
}
