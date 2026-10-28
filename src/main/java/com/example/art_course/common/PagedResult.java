package com.example.art_course.common;

import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Một trang kết quả cắt từ danh sách đã lọc và sắp xếp.
 * size == null nghĩa là không phân trang: trả cả danh sách như API cũ (FE cũ vẫn chạy).
 * shortcut: cắt trong bộ nhớ; chuyển sang Pageable ở tầng repository khi danh mục lên hàng nghìn mục.
 */
public record PagedResult<T>(List<T> items, int total, int page, int size) {

    public static final int MAX_SIZE = 50;

    public static <T> PagedResult<T> of(List<T> all, Integer page, Integer size) {
        if (size == null) {
            return new PagedResult<>(all, all.size(), 0, all.size());
        }
        int s = Math.min(Math.max(size, 1), MAX_SIZE);
        int p = Math.max(page == null ? 0 : page, 0);
        int from = (int) Math.min((long) p * s, all.size());
        int to = Math.min(from + s, all.size());
        return new PagedResult<>(all.subList(from, to), all.size(), p, s);
    }

    public boolean hasMore() {
        return (long) (page + 1) * size < total;
    }

    // Tổng số và cờ còn trang sau nằm ở header để body vẫn là ApiResponse<List<...>> như cũ.
    public ResponseEntity<ApiResponse<List<T>>> toResponse() {
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(total))
                .header("X-Page", String.valueOf(page))
                .header("X-Page-Size", String.valueOf(size))
                .header("X-Has-More", String.valueOf(hasMore()))
                .body(ApiResponse.ok(items));
    }
}
