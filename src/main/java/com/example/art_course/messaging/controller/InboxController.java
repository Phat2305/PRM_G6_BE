package com.example.art_course.messaging.controller;

import com.example.art_course.messaging.dto.ConversationSummary;
import com.example.art_course.messaging.service.InboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// MSG-05: hộp thư của người đang đăng nhập (Parent hoặc Teacher) và đánh dấu đã đọc.
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    @GetMapping("/me")
    public List<ConversationSummary> mine(Authentication auth) {
        return inboxService.mine(auth.getName());
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id, Authentication auth) {
        inboxService.markRead(id, auth.getName());
    }
}
