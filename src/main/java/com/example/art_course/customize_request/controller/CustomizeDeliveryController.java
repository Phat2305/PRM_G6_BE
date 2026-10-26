package com.example.art_course.customize_request.controller;

import com.example.art_course.customize_request.service.CustomizeDeliveryService;
import com.example.art_course.customize_request.service.CustomizeDeliveryService.DeliveryView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// CUS-11/12: gửi khóa riêng cho Staff duyệt, phụ huynh nhận hoặc chưa đồng ý, xác nhận hoàn thành.
@RestController
@RequestMapping("/api/customize-requests/{requestId}")
@RequiredArgsConstructor
public class CustomizeDeliveryController {

    private final CustomizeDeliveryService service;

    public record DeliverBody(UUID courseId) {}

    public record CompleteBody(UUID childId) {}

    public record RejectBody(String reason) {}

    // Giáo viên có báo giá được chấp nhận gửi khóa đã làm cho Staff duyệt (yêu cầu vẫn FUNDED tới khi Staff duyệt thì DELIVERED).
    @PostMapping("/deliver")
    public DeliveryView deliver(@PathVariable UUID requestId, @RequestBody DeliverBody body, Authentication auth) {
        return service.deliver(requestId, body.courseId(), auth.getName());
    }

    // Phụ huynh (hoặc giáo viên đó) xem khóa đã bàn giao: tên, mô tả, danh sách bài.
    @GetMapping("/delivery")
    public DeliveryView delivery(@PathVariable UUID requestId, Authentication auth) {
        return service.delivery(requestId, auth.getName());
    }

    // Phụ huynh chưa đồng ý với bản bàn giao: nêu lý do, gửi cho giáo viên qua tin nhắn, yêu cầu về FUNDED để sửa.
    @PostMapping("/reject-delivery")
    public DeliveryView rejectDelivery(@PathVariable UUID requestId, @RequestBody RejectBody body, Authentication auth) {
        return service.rejectDelivery(requestId, body.reason(), auth.getName());
    }

    // Phụ huynh gán cho bé và xác nhận hoàn thành (DELIVERED → COMPLETED).
    @PostMapping("/complete")
    public DeliveryView complete(@PathVariable UUID requestId, @RequestBody CompleteBody body, Authentication auth) {
        return service.complete(requestId, body.childId(), auth.getName());
    }
}
