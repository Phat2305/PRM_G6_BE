package com.example.art_course.course;

import com.example.art_course.course.dto.CreateCourseRequest;
import com.example.art_course.course.dto.UpdateCourseRequest;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.course.service.CourseService;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.lesson.dto.UpdateLessonRequest;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.lesson.service.LessonService;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Staff duyệt khóa học: giáo viên chỉ đặt DRAFT/PENDING_REVIEW, gửi lại tối đa 3 lần, sửa khóa công khai thì chờ duyệt lại.
class CourseReviewRulesTest {

    private final CourseRepository courses = mock(CourseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final CourseService service = new CourseService(courses, users, mock(CustomizeRequestRepository.class),
            mock(OfferRepository.class), lessons);
    private final LessonService lessonService = new LessonService(lessons, courses, users);

    private final UUID teacherId = UUID.randomUUID();
    private final Course course = Course.builder().id(UUID.randomUUID()).teacherId(teacherId).name("Màu nước")
            .price(new BigDecimal("100000")).status("DRAFT").build();

    private String login(UUID id, String role) {
        when(users.findById(id)).thenReturn(Optional.of(new User(id, "u", "h", role, "ACTIVE", null, null)));
        return id.toString();
    }

    private Lesson readyLesson() {
        return Lesson.builder().id(UUID.randomUUID()).courseId(course.getId()).title("Bài 1").videoStatus("READY").build();
    }

    private UpdateCourseRequest status(String s) {
        UpdateCourseRequest r = new UpdateCourseRequest();
        r.setStatus(s);
        return r;
    }

    private void stubCourse() {
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
        when(courses.save(any())).thenAnswer(i -> i.getArgument(0));
        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of(readyLesson()));
    }

    @Test
    void onlyTeacherCreatesCourses_andNewCourseIsAlwaysDraft() {
        when(courses.save(any())).thenAnswer(i -> i.getArgument(0));
        CreateCourseRequest req = new CreateCourseRequest();
        req.setName("Màu nước");
        req.setStatus("PUBLISHED");

        assertThrows(ResponseStatusException.class, () -> service.createCourse(req, login(UUID.randomUUID(), "PARENT")));
        String teacher = login(teacherId, "TEACHER");
        assertThrows(ResponseStatusException.class, () -> service.createCourse(req, teacher)); // không tự công khai

        req.setStatus("PENDING_REVIEW"); // chưa có bài học nên chưa gửi duyệt được
        assertThrows(ResponseStatusException.class, () -> service.createCourse(req, teacher));

        req.setStatus(null);
        assertEquals("DRAFT", service.createCourse(req, teacher).getStatus());
    }

    @Test
    void teacherCannotPublishOrRejectOwnCourse() {
        stubCourse();
        String teacher = login(teacherId, "TEACHER");

        assertThrows(ResponseStatusException.class, () -> service.updateCourse(course.getId(), status("PUBLISHED"), teacher));
        assertThrows(ResponseStatusException.class, () -> service.updateCourse(course.getId(), status("REJECTED"), teacher));
        assertEquals("DRAFT", course.getStatus());
    }

    @Test
    void submitNeedsLessonsAndReadyVideos() {
        stubCourse();
        String teacher = login(teacherId, "TEACHER");

        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.updateCourse(course.getId(), status("PENDING_REVIEW"), teacher));

        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of(
                Lesson.builder().title("Bài 1").videoStatus("PROCESSING").build()));
        assertThrows(ResponseStatusException.class, () -> service.updateCourse(course.getId(), status("PENDING_REVIEW"), teacher));

        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of(readyLesson()));
        assertEquals("PENDING_REVIEW", service.updateCourse(course.getId(), status("PENDING_REVIEW"), teacher).getStatus());
    }

    @Test
    void rejectedCourseCanBeResubmittedOnlyBeforeThirdRejection() {
        stubCourse();
        String teacher = login(teacherId, "TEACHER");
        course.setStatus("REJECTED");
        course.setRejectionCount(2);
        assertEquals("PENDING_REVIEW", service.updateCourse(course.getId(), status("PENDING_REVIEW"), teacher).getStatus());

        course.setStatus("REJECTED");
        course.setRejectionCount(3);
        assertThrows(ResponseStatusException.class, () -> service.updateCourse(course.getId(), status("PENDING_REVIEW"), teacher));
        // Không né giới hạn bằng cách về DRAFT rồi gửi lại.
        assertThrows(ResponseStatusException.class, () -> service.updateCourse(course.getId(), status("DRAFT"), teacher));
        assertEquals("REJECTED", course.getStatus());
    }

    @Test
    void editingAPublishedCourseSendsItBackToReview() {
        stubCourse();
        String teacher = login(teacherId, "TEACHER");
        course.setStatus("PUBLISHED");

        UpdateCourseRequest same = new UpdateCourseRequest();
        same.setName("Màu nước"); // không đổi gì
        assertEquals("PUBLISHED", service.updateCourse(course.getId(), same, teacher).getStatus());

        UpdateCourseRequest edit = new UpdateCourseRequest();
        edit.setPrice(new BigDecimal("150000"));
        assertEquals("PENDING_REVIEW", service.updateCourse(course.getId(), edit, teacher).getStatus());
    }

    @Test
    void editingALessonOfAPublishedCourseSendsItBackToReview_andClientCannotSetVideoUrl() {
        course.setStatus("PUBLISHED");
        Lesson lesson = readyLesson();
        lesson.setVideoUrl("/api/videos/hls/real/master.m3u8");
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
        when(lessons.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(lessons.save(any())).thenAnswer(i -> i.getArgument(0));
        String teacher = login(teacherId, "TEACHER");

        UpdateLessonRequest edit = new UpdateLessonRequest();
        edit.setTitle("Bài 1 (sửa)");
        edit.setVideoUrl("/api/videos/hls/someone-elses/master.m3u8");
        lessonService.updateLesson(lesson.getId(), edit, teacher);

        assertEquals("PENDING_REVIEW", course.getStatus());
        assertEquals("/api/videos/hls/real/master.m3u8", lesson.getVideoUrl());
        verify(courses).save(course);
    }

    @Test
    void deliveredCustomizeCourseIsLockedForTheTeacher() {
        stubCourse();
        String teacher = login(teacherId, "TEACHER");
        course.setStatus("PUBLISHED");
        course.setCustomizeRequestId(UUID.randomUUID());

        UpdateCourseRequest edit = new UpdateCourseRequest();
        edit.setDescription("Mô tả mới");
        assertThrows(ResponseStatusException.class, () -> service.updateCourse(course.getId(), edit, teacher));
        assertThrows(ResponseStatusException.class, () -> service.updateCourse(course.getId(), status("DRAFT"), teacher));
        assertEquals("PUBLISHED", course.getStatus());
    }

    @Test
    void createLessonDropsVideoFieldsSentByClient() {
        stubCourse();
        String teacher = login(teacherId, "TEACHER");
        when(lessons.save(any())).thenAnswer(i -> i.getArgument(0));
        Lesson incoming = Lesson.builder().courseId(course.getId()).title("Bài mới")
                .videoUrl("/api/videos/hls/x/master.m3u8").videoStatus("READY").build();

        Lesson created = lessonService.createLesson(incoming, teacher);

        assertNull(created.getVideoUrl());
        assertNull(created.getVideoStatus());
    }
}
