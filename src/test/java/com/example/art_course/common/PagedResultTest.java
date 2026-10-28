package com.example.art_course.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PagedResultTest {

    private static final List<Integer> TWENTY_FIVE = IntStream.range(0, 25).boxed().toList();

    @Test
    @DisplayName("không truyền size: trả cả danh sách như API cũ")
    void noSizeReturnsAll() {
        var r = PagedResult.of(TWENTY_FIVE, null, null);
        assertEquals(25, r.items().size());
        assertEquals(25, r.total());
        assertFalse(r.hasMore());
    }

    @Test
    @DisplayName("chia trang: trang đầu, giữa, cuối; cờ còn trang sau đúng")
    void slicesPages() {
        var first = PagedResult.of(TWENTY_FIVE, 0, 10);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), first.items());
        assertTrue(first.hasMore());

        var last = PagedResult.of(TWENTY_FIVE, 2, 10);
        assertEquals(List.of(20, 21, 22, 23, 24), last.items());
        assertEquals(25, last.total());
        assertFalse(last.hasMore());
    }

    @Test
    @DisplayName("trang vượt cuối: trả rỗng, không ném lỗi")
    void pastEndIsEmpty() {
        var r = PagedResult.of(TWENTY_FIVE, 99, 10);
        assertTrue(r.items().isEmpty());
        assertEquals(25, r.total());
        assertFalse(r.hasMore());
    }

    @Test
    @DisplayName("page âm về 0, size ngoài khoảng bị kẹp 1..50")
    void clampsBadInput() {
        assertEquals(0, PagedResult.of(TWENTY_FIVE, -3, 10).page());
        assertEquals(1, PagedResult.of(TWENTY_FIVE, 0, 0).items().size());
        assertEquals(25, PagedResult.of(TWENTY_FIVE, 0, 1000).items().size());
        assertEquals(PagedResult.MAX_SIZE, PagedResult.of(TWENTY_FIVE, 0, 1000).size());
    }

    @Test
    @DisplayName("toResponse đặt header tổng số và cờ còn trang sau")
    void responseHeaders() {
        var res = PagedResult.of(TWENTY_FIVE, 0, 10).toResponse();
        assertEquals("25", res.getHeaders().getFirst("X-Total-Count"));
        assertEquals("true", res.getHeaders().getFirst("X-Has-More"));
        assertEquals(10, res.getBody().getData().size());
    }
}
