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

    @Transactional(readOnly = true)
    public List<Supply> listSupplies() {
        return supplyRepository.findByStatusIgnoreCase("ACTIVE");
    }

    @Transactional(readOnly = true)
    public Supply getSupplyDetail(UUID supplyId) {
        return supplyRepository.findById(supplyId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "SUPPLY_NOT_FOUND", "Không tìm thấy dụng cụ vẽ"));
    }
}
