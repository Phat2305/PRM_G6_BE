package com.example.art_course.common.image;

import com.example.art_course.common.AppError;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping(value = "/api/supplies/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public Map<String, String> uploadSupplyImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                                 Authentication auth) {
        return Map.of("imageUrl", imageService.uploadSupplyImage(id, currentUserId(auth), file));
    }

    @PostMapping(value = "/api/courses/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public Map<String, String> uploadCourseCover(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                                 Authentication auth) {
        return Map.of("coverImageUrl", imageService.uploadCourseCover(id, currentUserId(auth), file));
    }

    // Công khai (SecurityConfig): ảnh danh mục hiện cho mọi người dùng, kể cả chưa đăng nhập.
    @GetMapping("/api/images/{kind}/{id}")
    public ResponseEntity<Resource> image(@PathVariable String kind, @PathVariable UUID id,
                                          Authentication auth) {
        UUID viewerId = auth != null && auth.getPrincipal() instanceof UUID userId ? userId : null;
        ResponseInputStream<GetObjectResponse> object = imageService.read(kind, id, viewerId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.response().contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(new InputStreamResource(object));
    }

    private UUID currentUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UUID id)) {
            throw new AppError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token không hợp lệ");
        }
        return id;
    }
}
