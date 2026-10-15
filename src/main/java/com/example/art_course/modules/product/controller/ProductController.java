package com.example.art_course.modules.product.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supplies")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<List<Supply>> listSupplies() {
        return ApiResponse.ok(productService.listSupplies());
    }

    @GetMapping("/{supplyId}")
    public ApiResponse<Supply> getSupplyDetail(@PathVariable UUID supplyId) {
        return ApiResponse.ok(productService.getSupplyDetail(supplyId));
    }
}
