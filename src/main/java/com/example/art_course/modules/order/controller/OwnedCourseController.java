package com.example.art_course.modules.order.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.order.service.OwnedCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// SHOP-04: trang "Khóa học của tôi" của phụ huynh. Tách khỏi OrderController để không đổi constructor của nó.
@RestController
@RequestMapping("/api/v1/orders/my-courses")
@RequiredArgsConstructor
public class OwnedCourseController {

    private final OwnedCourseService ownedCourseService;

    @GetMapping
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<List<OwnedCourseService.OwnedCourseView>> myCourses(@AuthenticationPrincipal UUID parentId) {
        return ApiResponse.ok(ownedCourseService.list(parentId));
    }
}
