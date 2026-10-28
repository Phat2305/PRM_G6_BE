package com.example.art_course.modules.product.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supplies")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // Không truyền size thì trả cả danh sách như cũ. Có size: ?page=0&size=10&q=...;
    // tổng số và cờ còn trang sau nằm ở header X-Total-Count, X-Has-More.
    @GetMapping
    public ResponseEntity<ApiResponse<List<Supply>>> listSupplies(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return productService.listSupplies(q, page, size).toResponse();
    }

    @GetMapping("/{supplyId}")
    public ApiResponse<Supply> getSupplyDetail(@PathVariable UUID supplyId) {
        return ApiResponse.ok(productService.getSupplyDetail(supplyId));
    }
}
