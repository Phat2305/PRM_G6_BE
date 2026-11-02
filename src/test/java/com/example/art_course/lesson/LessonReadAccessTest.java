package com.example.art_course.lesson;

import com.example.art_course.course.service.CourseService;
import com.example.art_course.lesson.controller.LessonController;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.service.LessonService;
import com.example.art_course.video.service.VideoAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Đề bài tập, ảnh mẫu và đường dẫn video của bài trả phí chỉ dành cho người có quyền học khóa đó.
class LessonReadAccessTest {

    private final LessonService lessons = mock(LessonService.class);
    private final CourseService courses = mock(CourseService.class);
    private final VideoAccessService access = mock(VideoAccessService.class);
    private final LessonController controller = new LessonController(lessons, courses, access);

    private final UUID courseId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Authentication login(String role) {
        return new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Lesson lesson(boolean trial) {
        return Lesson.builder().id(UUID.randomUUID()).courseId(courseId).title("Bài").isTrial(trial)
                .hasAssignment(true).assignmentTitle("Vẽ mây").assignmentPrompt("Vẽ đám mây bằng màu nước")
                .sampleImageUrl("/api/images/lessons/" + UUID.randomUUID())
                .videoUrl("/api/videos/hls/" + UUID.randomUUID() + "/master.m3u8")
                .videoStatus("READY").durationSeconds(120).status("PUBLISHED").build();
    }

    @Test
    void aViewerWithoutAccessSeesNeitherTheAssignmentNorThePaidVideo() {
        Lesson paid = lesson(false);
        Lesson trial = lesson(true);
        when(lessons.getLessonsByCourseId(courseId)).thenReturn(List.of(paid, trial));
        when(access.canViewCourse(courseId, userId, "PARENT")).thenReturn(false);

        var result = controller.getLessonsByCourseId(courseId, login("PARENT"));

        var hiddenPaid = result.get(0);
        assertNull(hiddenPaid.getVideoUrl());
        assertNull(hiddenPaid.getAssignmentTitle());
        assertNull(hiddenPaid.getAssignmentPrompt());
        assertNull(hiddenPaid.getSampleImageUrl());
        // Vẫn thấy bài có bài tập và thông tin để hiển thị danh sách.
        assertEquals(Boolean.TRUE, hiddenPaid.getHasAssignment());
        assertEquals("Bài", hiddenPaid.getTitle());
        assertEquals("READY", hiddenPaid.getVideoStatus());
        assertEquals(120, hiddenPaid.getDurationSeconds());

        // Bài xem thử: giữ đường dẫn video (ai đăng nhập cũng xem được), nhưng đề bài tập vẫn ẩn.
        var openTrial = result.get(1);
        assertEquals(trial.getVideoUrl(), openTrial.getVideoUrl());
        assertNull(openTrial.getAssignmentPrompt());
        assertNull(openTrial.getSampleImageUrl());
    }

    @Test
    void aViewerWithAccessSeesEverything() {
        Lesson paid = lesson(false);
        when(lessons.getLessonsByCourseId(courseId)).thenReturn(List.of(paid));
        when(access.canViewCourse(courseId, userId, "CHILD")).thenReturn(true);

        var seen = controller.getLessonsByCourseId(courseId, login("CHILD")).get(0);

        assertEquals(paid.getVideoUrl(), seen.getVideoUrl());
        assertEquals("Vẽ mây", seen.getAssignmentTitle());
        assertEquals("Vẽ đám mây bằng màu nước", seen.getAssignmentPrompt());
        assertEquals(paid.getSampleImageUrl(), seen.getSampleImageUrl());
    }

    @Test
    void singleLessonFollowsTheSameRule() {
        Lesson paid = lesson(false);
        when(lessons.getLessonById(paid.getId())).thenReturn(paid);
        when(access.canViewCourse(courseId, userId, "PARENT")).thenReturn(false);

        var hidden = controller.getLessonById(paid.getId(), login("PARENT"));
        assertNull(hidden.getVideoUrl());
        assertNull(hidden.getAssignmentPrompt());

        when(access.canViewCourse(courseId, userId, "TEACHER")).thenReturn(true);
        var owner = controller.getLessonById(paid.getId(), login("TEACHER"));
        assertEquals(paid.getVideoUrl(), owner.getVideoUrl());
        assertEquals("Vẽ đám mây bằng màu nước", owner.getAssignmentPrompt());
    }

    // CUS-10: khóa làm riêng chỉ hiện với giáo viên và phụ huynh của yêu cầu, kể cả qua API bài học.
    @Test
    void lessonsOfACustomizeCourseFollowTheCourseVisibilityRule() {
        when(courses.getCourseForViewer(courseId, userId.toString()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        var error = assertThrows(ResponseStatusException.class,
                () -> controller.getLessonsByCourseId(courseId, login("PARENT")));

        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(lessons);

        Lesson one = lesson(false);
        when(lessons.getLessonById(one.getId())).thenReturn(one);
        assertThrows(ResponseStatusException.class,
                () -> controller.getLessonById(one.getId(), login("PARENT")));
    }
}
