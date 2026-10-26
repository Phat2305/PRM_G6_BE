package com.example.art_course.modules.staff;

import com.example.art_course.common.AppError;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.contracts.IModerationContract;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.staff.service.CourseModerationService;
import com.example.art_course.modules.staff.service.RequestModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// STF-01/07: Staff duyệt khóa học và bài đăng; từ chối kèm lý do, đủ 3 lần thì từ chối hẳn.
class ReviewModerationTest {

    private final CourseRepository courses = mock(CourseRepository.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final IModerationContract moderation = mock(IModerationContract.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final TeacherNames names = mock(TeacherNames.class);
    private final CourseModerationService courseService =
            new CourseModerationService(courses, lessons, requests, moderation, notifications, names);
    private final RequestModerationService requestService =
            new RequestModerationService(requests, moderation, notifications, names);

    private final UUID staffId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private final Course course = Course.builder().id(UUID.randomUUID()).teacherId(teacherId).name("Màu nước")
            .price(new BigDecimal("100000")).status("PENDING_REVIEW").build();
    private final CustomizeRequest request = CustomizeRequest.builder().id(UUID.randomUUID()).parentId(parentId)
            .title("Vẽ mèo").status("PENDING_REVIEW").build();

    private void stubCourse(boolean videosReady) {
        when(courses.findByIdForUpdate(course.getId())).thenReturn(Optional.of(course));
        when(lessons.findAllByCourseId(course.getId())).thenReturn(List.of(
                Lesson.builder().title("Bài 1").videoStatus(videosReady ? "READY" : "PROCESSING").build()));
    }

    // ---- khóa học ----

    @Test
    void approveStandardCourse_publishesLogsAndNotifiesTeacher() {
        stubCourse(true);
        course.setRejectionCount(2);
        course.setReviewNote("cũ");

        courseService.approve(course.getId(), staffId);

        assertEquals("PUBLISHED", course.getStatus());
        assertEquals(0, course.getRejectionCount());
        assertNull(course.getReviewNote());
        verify(courses).save(course);
        verify(moderation).recordModerationReview(staffId, "COURSE", course.getId(), "APPROVED", null);
        verify(notifications).notifyCourseApproved(course.getId(), teacherId, "Màu nước");
    }

    @Test
    void approveCustomizeCourse_movesRequestToDeliveredAndNotifiesParent() {
        stubCourse(true);
        course.setCustomizeRequestId(request.getId());
        request.setStatus("FUNDED");
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));

        courseService.approve(course.getId(), staffId);

        assertEquals("PUBLISHED", course.getStatus());
        assertEquals("DELIVERED", request.getStatus());
        verify(notifications).notifyCustomizeDelivered(request.getId(), parentId, "Màu nước");
        verify(notifications, never()).notifyCourseApproved(any(), any(), anyString());
    }

    @Test
    void approveCustomizeCourse_refusesWhenRequestIsNoLongerFunded() {
        stubCourse(true);
        course.setCustomizeRequestId(request.getId());
        request.setStatus("CANCELLED");
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(AppError.class, () -> courseService.approve(course.getId(), staffId));
        assertEquals("PENDING_REVIEW", course.getStatus());
    }

    @Test
    void approveRefusesVideosNotReady_andCoursesNotWaitingForReview() {
        stubCourse(false);
        assertThrows(ResponseStatusException.class, () -> courseService.approve(course.getId(), staffId));
        assertEquals("PENDING_REVIEW", course.getStatus());

        stubCourse(true);
        course.setStatus("DRAFT");
        assertThrows(AppError.class, () -> courseService.approve(course.getId(), staffId));
        assertThrows(AppError.class, () -> courseService.reject(course.getId(), staffId, "x"));
        verify(moderation, never()).recordModerationReview(any(), anyString(), any(), anyString(), any());
    }

    @Test
    void rejectCourse_countsAttemptsKeepsReasonAndTellsTeacherHowManyLeft() {
        stubCourse(true);

        courseService.reject(course.getId(), staffId, "Video mờ");

        assertEquals("REJECTED", course.getStatus());
        assertEquals(1, course.getRejectionCount());
        assertEquals("Video mờ", course.getReviewNote());
        verify(moderation).recordModerationReview(staffId, "COURSE", course.getId(), "REJECTED", "Video mờ");
        verify(notifications).notifyCourseRejected(course.getId(), teacherId, "Màu nước", "Video mờ", 2);
    }

    @Test
    void thirdRejectionOfACustomizeCourseCancelsTheRequestSoParentCanAskForRefund() {
        stubCourse(true);
        course.setCustomizeRequestId(request.getId());
        course.setRejectionCount(2);
        request.setStatus("FUNDED");
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));

        courseService.reject(course.getId(), staffId, "Vẫn chưa đạt");

        assertEquals(3, course.getRejectionCount());
        assertEquals("CANCELLED", request.getStatus());
        verify(notifications).notifyCourseRejected(course.getId(), teacherId, "Màu nước", "Vẫn chưa đạt", 0);
        verify(notifications).notifyCustomizeFailed(request.getId(), parentId, "Màu nước");
    }

    @Test
    void thirdRejectionOfAStandardCourseDoesNotTouchAnyRequest() {
        stubCourse(true);
        course.setRejectionCount(2);

        courseService.reject(course.getId(), staffId, "Vẫn chưa đạt");

        verify(requests, never()).save(any());
        verify(notifications, never()).notifyCustomizeFailed(any(), any(), anyString());
    }

    // ---- bài đăng ----

    @Test
    void approveRequest_opensItForTeachersAndResetsAttempts() {
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        request.setRejectionCount(2);
        request.setReviewNote("cũ");

        requestService.approve(request.getId(), staffId);

        assertEquals("OPEN", request.getStatus());
        assertEquals(0, request.getRejectionCount());
        assertNull(request.getReviewNote());
        verify(moderation).recordModerationReview(staffId, "CUSTOMIZE_REQUEST", request.getId(), "APPROVED", null);
        verify(notifications).notifyRequestApproved(request.getId(), parentId, "Vẽ mèo");
    }

    @Test
    void rejectRequest_keepsReasonAndFinalAfterThirdRejection() {
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        requestService.reject(request.getId(), staffId, "Có số điện thoại");
        assertEquals("REJECTED", request.getStatus());
        assertEquals(1, request.getRejectionCount());
        verify(notifications).notifyRequestRejected(request.getId(), parentId, "Vẽ mèo", "Có số điện thoại", 2);

        request.setStatus("PENDING_REVIEW"); // chủ bài sửa và gửi lại
        request.setRejectionCount(2);
        requestService.reject(request.getId(), staffId, "Vẫn vi phạm");
        assertEquals(3, request.getRejectionCount());
        verify(notifications).notifyRequestRejected(request.getId(), parentId, "Vẽ mèo", "Vẫn vi phạm", 0);
    }

    @Test
    void requestNotWaitingForReviewCannotBeDecided() {
        request.setStatus("OPEN");
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThrows(AppError.class, () -> requestService.approve(request.getId(), staffId));
        assertThrows(AppError.class, () -> requestService.reject(request.getId(), staffId, "x"));
        verify(notifications, never()).notifyRequestApproved(any(), any(), anyString());
        verify(notifications, never()).notifyRequestRejected(any(), any(), anyString(), anyString(), anyInt());
    }
}
