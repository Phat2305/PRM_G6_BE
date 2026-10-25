package com.example.art_course.modules.course.controller;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.modules.course.entity.CourseReview;
import com.example.art_course.modules.course.service.CourseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Đánh giá khóa học: không lộ mã phụ huynh/bé của người khác, chỉ đánh dấu đánh giá của chính mình")
class CourseReviewControllerTest {

    private final CourseService courseService = mock(CourseService.class);
    private final CourseReviewController controller = new CourseReviewController(courseService);

    private final UUID courseId = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();

    private CourseReview review(UUID parentId, int rating, String comment) {
        return CourseReview.builder().id(UUID.randomUUID()).courseId(courseId).parentId(parentId)
                .childId(UUID.randomUUID()).rating(rating).comment(comment).build();
    }

    private Authentication as(String role) {
        return new UsernamePasswordAuthenticationToken(me, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("phụ huynh: danh sách có mine=true chỉ với đánh giá của người gọi")
    void marksOnlyMyReview() {
        when(courseService.getCourseReviews(courseId)).thenReturn(List.of(
                review(me, 5, "Hay"), review(UUID.randomUUID(), 3, null)));

        List<CourseReviewController.ReviewView> views = controller.getReviews(courseId, me, as("PARENT")).getData();

        assertEquals(2, views.size());
        assertTrue(views.get(0).mine());
        assertFalse(views.get(1).mine());
        assertEquals(5, views.get(0).rating());
        verify(courseService, never()).getCourseReviewsForTeacher(any(), any());
    }

    @Test
    @DisplayName("giáo viên: đi qua kiểm tra sở hữu khóa, không đọc thẳng danh sách công khai")
    void teacherGoesThroughOwnershipCheck() {
        when(courseService.getCourseReviewsForTeacher(courseId, me)).thenReturn(List.of(review(UUID.randomUUID(), 4, "Tốt")));

        List<CourseReviewController.ReviewView> views = controller.getReviews(courseId, me, as("TEACHER")).getData();

        assertEquals(1, views.size());
        assertFalse(views.get(0).mine());
        verify(courseService, never()).getCourseReviews(any());
    }

    @Test
    @DisplayName("bản trả về không có parentId hoặc childId")
    void viewHasNoAccountIds() {
        List<String> components = Arrays.stream(CourseReviewController.ReviewView.class.getRecordComponents())
                .map(c -> c.getName()).toList();

        assertFalse(components.contains("parentId"));
        assertFalse(components.contains("childId"));
    }

    @Test
    @DisplayName("gửi đánh giá: trả bản đã lọc và mine=true")
    void createReturnsView() {
        when(courseService.createCourseReview(courseId, me, 4, "Ổn")).thenReturn(review(me, 4, "Ổn"));
        CourseReviewController.CreateReviewRequest request = new CourseReviewController.CreateReviewRequest();
        request.setRating(4);
        request.setComment("Ổn");

        ApiResponse<CourseReviewController.ReviewView> response = controller.createReview(courseId, me, request);

        assertTrue(response.getData().mine());
        assertEquals(4, response.getData().rating());
    }
}
