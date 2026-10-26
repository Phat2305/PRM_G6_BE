package com.example.art_course.video;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.video.service.VideoAccessService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// LRN-03: video HLS chỉ cho người có quyền xem khóa; trước đây ai đăng nhập và biết hlsId đều xem được.
class VideoAccessServiceTest {

    private final LessonRepository lessons = mock(LessonRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final CustomizeRequestRepository requests = mock(CustomizeRequestRepository.class);
    private final IEnrollmentContract enrollment = mock(IEnrollmentContract.class);
    private final VideoAccessService service = new VideoAccessService(lessons, courses, children, requests, enrollment);

    private final UUID hlsId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final Course course = Course.builder().id(UUID.randomUUID()).teacherId(teacherId).status("PUBLISHED").build();

    private void lessonWith(boolean trial) {
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
        when(lessons.findAllByVideoUrlContaining("/hls/" + hlsId + "/")).thenReturn(List.of(
                Lesson.builder().courseId(course.getId()).isTrial(trial).build()));
    }

    @Test
    void staffAndAdminMayAlwaysStream_forModeration() {
        assertTrue(service.canStream(hlsId, UUID.randomUUID(), "STAFF"));
        assertTrue(service.canStream(hlsId, UUID.randomUUID(), "ADMIN"));
    }

    @Test
    void onlyTheOwningTeacherStreamsATeacherCourse() {
        lessonWith(false);
        assertTrue(service.canStream(hlsId, teacherId, "TEACHER"));
        assertFalse(service.canStream(hlsId, UUID.randomUUID(), "TEACHER"));
    }

    @Test
    void childNeedsEnrollment_parentNeedsAnEnrolledChild() {
        lessonWith(false);
        UUID childUser = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        Child child = new Child();
        child.setUserId(childUser);
        when(children.findAllByParentId(parent)).thenReturn(List.of(child));

        assertFalse(service.canStream(hlsId, childUser, "CHILD"));
        assertFalse(service.canStream(hlsId, parent, "PARENT"));

        when(enrollment.isEnrolled(childUser, course.getId())).thenReturn(true);
        assertTrue(service.canStream(hlsId, childUser, "CHILD"));
        assertTrue(service.canStream(hlsId, parent, "PARENT"));
    }

    @Test
    void trialLessonIsOpenToEveryLoggedInUser_unknownVideoToNobody() {
        lessonWith(true);
        assertTrue(service.canStream(hlsId, UUID.randomUUID(), "PARENT"));

        UUID unknown = UUID.randomUUID();
        when(lessons.findAllByVideoUrlContaining("/hls/" + unknown + "/")).thenReturn(List.of());
        assertFalse(service.canStream(unknown, UUID.randomUUID(), "PARENT"));
        assertFalse(service.canStream(unknown, UUID.randomUUID(), "CHILD"));
    }

    @Test
    void parentOfADeliveredCustomizeCourseMayPreviewItBeforeAccepting() {
        lessonWith(false);
        UUID parent = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        course.setCustomizeRequestId(requestId);
        CustomizeRequest request = CustomizeRequest.builder().id(requestId).parentId(parent).status("FUNDED").build();
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(children.findAllByParentId(parent)).thenReturn(List.of());

        assertFalse(service.canStream(hlsId, parent, "PARENT")); // Staff chưa duyệt

        request.setStatus("DELIVERED");
        assertTrue(service.canStream(hlsId, parent, "PARENT"));
        assertFalse(service.canStream(hlsId, UUID.randomUUID(), "PARENT")); // phụ huynh khác
    }

    @Test
    void missingUserOrRoleIsDenied() {
        lessonWith(true);
        assertFalse(service.canStream(hlsId, null, "PARENT"));
        assertFalse(service.canStream(hlsId, UUID.randomUUID(), null));
    }
}
