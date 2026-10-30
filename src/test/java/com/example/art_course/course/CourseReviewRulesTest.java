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
    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final com.example.art_course.customize_request.repository.CustomizeDeliveryHistoryRepository history =
            mock(com.example.art_course.customize_request.repository.CustomizeDeliveryHistoryRepository.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final CourseService service = new CourseService(courses, users, requests,
            history, offers, lessons);
    private final LessonService lessonService = new LessonService(lessons, courses, users, service);

    @Test
    void draftIsVisibleOnlyToItsTeacher() {
        stubCourse();
        assertSame(course, service.getVisibleCourseById(course.getId(), teacherId.toString()));
        assertThrows(ResponseStatusException.class,
                () -> service.getVisibleCourseById(course.getId(), UUID.randomUUID().toString()));
    }

    @Test
    void deliveredCustomizeCanBeEditedButCompletedCannot() {
        stubCourse();
        String teacher = login(teacherId, "TEACHER");
        UUID requestId = UUID.randomUUID();
        course.setCustomizeRequestId(requestId);
        course.setStatus("PUBLISHED");
        var request = com.example.art_course.customize_request.entity.CustomizeRequest.builder()
                .id(requestId).status("DELIVERED").build();
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        var delivered = com.example.art_course.customize_request.entity.CustomizeDeliveryHistory.builder()
                .requestId(requestId).courseId(course.getId()).build();
        when(history.findFirstByRequestIdAndResolvedAtIsNullOrderByDeliveredAtDesc(requestId))
                .thenReturn(Optional.of(delivered));
        UpdateCourseRequest same = new UpdateCourseRequest();
        same.setName(course.getName());
        assertEquals("PUBLISHED", service.updateCourse(course.getId(), same, teacher).getStatus());
        assertEquals("DELIVERED", request.getStatus());

        UpdateCourseRequest edit = new UpdateCourseRequest();
        edit.setDescription("Bản sửa");
        assertEquals("DRAFT", service.updateCourse(course.getId(), edit, teacher).getStatus());
        assertEquals("FUNDED", request.getStatus());
        assertEquals("REVISED", delivered.getResult());
        assertNotNull(delivered.getResolvedAt());

        request.setStatus("COMPLETED");
        edit.setDescription("Bản sửa tiếp");
        assertThrows(ResponseStatusException.class, () -> service.updateCourse(course.getId(), edit, teacher));
    }

    private final UUID teacherId = UUID.randomUUID();
    private final Course course = Course.builder().id(UUID.randomUUID()).teacherId(teacherId).name("Màu nước")
            .price(new BigDecimal("100000")).coverImageUrl("/api/images/courses/x").status("DRAFT").build();

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
    void secondCourseCannotBeLinkedToTheSameCustomizeRequest() {
        UUID requestId = UUID.randomUUID();
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(
                com.example.art_course.customize_request.entity.CustomizeRequest.builder()
                        .id(requestId).status("FUNDED").build()));
        when(offers.findAllByRequestIdAndTeacherId(requestId, teacherId)).thenReturn(List.of(
                com.example.art_course.offer.entity.Offer.builder().status("accepted").build()));
        when(courses.findFirstByCustomizeRequestId(requestId)).thenReturn(Optional.of(course));
        CreateCourseRequest draft = new CreateCourseRequest();
        draft.setName("Khóa khác");
        draft.setCustomizeRequestId(requestId);

        assertThrows(ResponseStatusException.class,
                () -> service.createCourse(draft, login(teacherId, "TEACHER")));
        verify(courses, never()).save(any());
    }

    @Test
    void deliveredCustomizeCourseCannotBeDeletedAndReplaced() {
        stubCourse();
        UUID requestId = UUID.randomUUID();
        course.setCustomizeRequestId(requestId);
        when(history.existsByRequestId(requestId)).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> service.deleteCourse(course.getId(), login(teacherId, "TEACHER")));
        verify(courses, never()).delete(course);
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
    void submitNeedsACoverImage_exceptForCustomizeCourses() {
        stubCourse();
        String teacher = login(teacherId, "TEACHER");

        course.setCoverImageUrl(null);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.updateCourse(course.getId(), status("PENDING_REVIEW"), teacher));
        assertEquals("Khóa học cần có ảnh bìa", e.getReason());
        assertEquals("DRAFT", course.getStatus());

        // Khóa customize chỉ có một người nhận, không lên danh mục nên không cần ảnh bìa.
        course.setCustomizeRequestId(UUID.randomUUID());
        when(requests.findByIdForUpdate(course.getCustomizeRequestId())).thenReturn(Optional.of(
                com.example.art_course.customize_request.entity.CustomizeRequest.builder()
                        .id(course.getCustomizeRequestId()).status("FUNDED").build()));
        assertEquals("PENDING_REVIEW", service.updateCourse(course.getId(), status("PENDING_REVIEW"), teacher).getStatus());
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
    void missingCustomizeRequestBlocksEdit() {
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
