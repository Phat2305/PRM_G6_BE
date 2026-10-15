package com.example.art_course.modules.cart.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.cart.entity.CartItem;
import com.example.art_course.modules.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<List<CartItem>> getCart(@AuthenticationPrincipal UUID parentId) {
        return ApiResponse.ok(cartService.getCartItems(parentId));
    }

    @PostMapping("/items")
    public ApiResponse<CartItem> addToCart(
            @AuthenticationPrincipal UUID parentId,
            @RequestBody CartService.AddToCartInput input
    ) {
        return ApiResponse.ok("Đã thêm vào giỏ hàng", cartService.addToCart(parentId, input));
    }

    @PatchMapping("/items/{itemId}")
    public ApiResponse<CartItem> updateItemQuantity(
            @AuthenticationPrincipal UUID parentId,
            @PathVariable UUID itemId,
            @RequestBody CartService.UpdateQuantityInput input
    ) {
        return ApiResponse.ok("Cập nhật số lượng thành công", cartService.updateItemQuantity(parentId, itemId, input.getQuantity()));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Void> removeItem(
            @AuthenticationPrincipal UUID parentId,
            @PathVariable UUID itemId
    ) {
        cartService.removeItem(parentId, itemId);
        return ApiResponse.ok("Đã xóa mục khỏi giỏ hàng", null);
    }
}
