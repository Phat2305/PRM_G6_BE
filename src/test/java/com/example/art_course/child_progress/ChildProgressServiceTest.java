package com.example.art_course.child_progress;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.child_progress.dto.ChildProgressResponse;
import com.example.art_course.child_progress.dto.CourseProgressResponse;
import com.example.art_course.child_progress.dto.UpdateProgressRequest;
import com.example.art_course.child_progress.entity.ChildProgress;
import com.example.art_course.child_progress.entity.ChildProgressId;
import com.example.art_course.child_progress.repository.ChildProgressRepository;
import com.example.art_course.child_progress.service.ChildProgressService;
import com.example.art_course.common.AppError;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.enrollment.entity.Enrollment;
import com.example.art_course.enrollment.entity.EnrollmentId;
import com.example.art_course.enrollment.repository.EnrollmentRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Tiến độ học: chỉ bé, phụ huynh của bé (và giáo viên của khóa khi xem) được truy cập")
class ChildProgressServiceTest {

    private final ChildProgressRepository progressRepo = mock(ChildProgressRepository.class);
    private final ChildRepository childRepo = mock(ChildRepository.class);
    private final LessonRepository lessonRepo = mock(LessonRepository.class);
    private final CourseRepository courseRepo = mock(CourseRepository.class);
    private final EnrollmentRepository enrollRepo = mock(EnrollmentRepository.class);
    private final org.springframework.context.ApplicationEventPublisher events =
            mock(org.springframework.context.ApplicationEventPublisher.class);
    private final ChildProgressService service =
            new ChildProgressService(progressRepo, childRepo, lessonRepo, courseRepo, enrollRepo,
                    events);

    @Test
    void completingLastLessonEmitsCourseCompletion() {
        enroll("active");
        when(lessonRepo.findAllByCourseId(courseId)).thenReturn(List.of(lesson));
        var completed = new ChildProgress(new ChildProgressId(childId, lessonId), 90, true);
        var before = new ChildProgress(new ChildProgressId(childId, lessonId), 30, false);
        when(progressRepo.findByIdChildIdAndIdLessonId(childId, lessonId))
                .thenReturn(Optional.of(before), Optional.of(completed));
        when(progressRepo.findAllByIdChildId(childId)).thenReturn(List.of(completed));
        service.updateProgress(childId, childId, lessonId, request(90, true));
        verify(events).publishEvent(any(com.example.art_course.contracts.events.CourseCompletedEvent.class));
    }

    private final UUID childId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        Child child = Child.builder().userId(childId).parentId(parentId).displayName("Bé Na").build();
        when(childRepo.findByUserId(childId)).thenReturn(Optional.of(child));
        when(childRepo.findByUserIdAndParentId(childId, parentId)).thenReturn(Optional.of(child));
        lesson = Lesson.builder().id(lessonId).courseId(courseId).isTrial(false).build();
        when(lessonRepo.findById(lessonId)).thenReturn(Optional.of(lesson));
        Course course = mock(Course.class);
        when(course.getTeacherId()).thenReturn(teacherId);
        when(courseRepo.findById(courseId)).thenReturn(Optional.of(course));
        when(progressRepo.findByIdChildIdAndIdLessonId(childId, lessonId)).thenReturn(Optional.of(
                new ChildProgress(new ChildProgressId(childId, lessonId), 30, false)));
    }

    private void enroll(String status) {
        when(enrollRepo.findByIdChildIdAndIdCourseId(childId, courseId)).thenReturn(
                Optional.of(new Enrollment(new EnrollmentId(childId, courseId), status)));
    }

    private UpdateProgressRequest request(int seconds, boolean completed) {
        UpdateProgressRequest r = new UpdateProgressRequest();
        r.setWatchedSeconds(seconds);
        r.setIsCompleted(completed);
        return r;
    }

    private static void assertForbidden(Runnable action) {
        AppError error = assertThrows(AppError.class, action::run);
        assertEquals(403, error.getStatus().value());
    }

    @Test
    @DisplayName("xem: bé, phụ huynh của bé và giáo viên của khóa được; người lạ bị 403")
    void readAccess() {
        assertNotNull(service.getProgress(childId, childId, lessonId));
        assertNotNull(service.getProgress(parentId, childId, lessonId));
        assertNotNull(service.getProgress(teacherId, childId, lessonId));
        assertForbidden(() -> service.getProgress(stranger, childId, lessonId));
        assertForbidden(() -> service.getByChildId(stranger, childId));
        assertForbidden(() -> service.getCourseProgress(stranger, childId, courseId));
        // giáo viên không xem được toàn bộ tiến độ của bé (mọi khóa), chỉ theo khóa của mình
        assertForbidden(() -> service.getByChildId(teacherId, childId));
    }

    @Test
    @DisplayName("ghi: chỉ chính bé; phụ huynh hay người lạ đều bị 403 và không ghi gì")
    void onlyChildWrites() {
        enroll("active");
        assertForbidden(() -> service.updateProgress(parentId, childId, lessonId, request(10, false)));
        assertForbidden(() -> service.updateProgress(stranger, childId, lessonId, request(10, false)));
        verify(progressRepo, never()).upsert(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("ghi: bé chưa được học khóa (và bài không phải học thử) thì NOT_ENROLLED")
    void notEnrolled() {
        AppError error = assertThrows(AppError.class,
                () -> service.updateProgress(childId, childId, lessonId, request(10, true)));
        assertEquals("NOT_ENROLLED", error.getErrorCode());
        enroll("cancelled");
        assertThrows(AppError.class,
                () -> service.updateProgress(childId, childId, lessonId, request(10, true)));
        verify(progressRepo, never()).upsert(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("ghi: đã ghi danh (không phân biệt hoa thường) hoặc bài học thử thì được")
    void enrolledOrTrialMayWrite() {
        enroll("ACTIVE");
        ChildProgressResponse response = service.updateProgress(childId, childId, lessonId, request(45, true));
        assertEquals(30, response.getWatchedSeconds()); // đọc lại từ DB, upsert do SQL lo
        verify(progressRepo).upsert(childId, lessonId, 45, true);

        reset(enrollRepo);
        lesson.setIsTrial(true);
        service.updateProgress(childId, childId, lessonId, request(5, false));
        verify(progressRepo).upsert(childId, lessonId, 5, false);
    }

    @Test
    @DisplayName("danh sách học viên: chỉ giáo viên của khóa; mỗi bé đang học có số bài xong/tổng, bé đã hủy không hiện")
    void courseRoster() {
        UUID child2 = UUID.randomUUID();
        UUID cancelled = UUID.randomUUID();
        UUID l2 = UUID.randomUUID();
        when(lessonRepo.findAllByCourseId(courseId)).thenReturn(List.of(
                lesson, Lesson.builder().id(l2).courseId(courseId).build()));
        when(enrollRepo.findAllByIdCourseId(courseId)).thenReturn(List.of(
                new Enrollment(new EnrollmentId(childId, courseId), "active"),
                new Enrollment(new EnrollmentId(child2, courseId), "ACTIVE"),
                new Enrollment(new EnrollmentId(cancelled, courseId), "cancelled")));
        when(progressRepo.findAllByIdLessonIdIn(any())).thenReturn(List.of(
                new ChildProgress(new ChildProgressId(childId, lessonId), 100, true),
                new ChildProgress(new ChildProgressId(childId, l2), 100, true),
                new ChildProgress(new ChildProgressId(child2, lessonId), 10, false),
                new ChildProgress(new ChildProgressId(cancelled, lessonId), 100, true)));

        List<CourseProgressResponse> roster = service.getCourseRoster(teacherId, courseId);

        assertEquals(2, roster.size());
        assertEquals(childId, roster.get(0).getChildId());
        assertEquals(2, roster.get(0).getCompletedLessons());
        assertEquals(100, roster.get(0).getPercent());
        assertEquals(0, roster.get(1).getCompletedLessons());
        assertEquals(2, roster.get(1).getTotalLessons());

        assertForbidden(() -> service.getCourseRoster(parentId, courseId));
        assertForbidden(() -> service.getCourseRoster(stranger, courseId));
    }

    @Test
    @DisplayName("hoàn thành: bài có thời lượng thì phải xem ít nhất 90% (tính cả phần đã báo trước đó)")
    void completionNeedsMostOfTheVideo() {
        enroll("active");
        lesson.setDurationSeconds(100);

        // báo "xong" khi mới tới giây 50 (và trước đó mới xem 30): chỉ lưu giây xem, không đánh dấu xong
        service.updateProgress(childId, childId, lessonId, request(50, true));
        verify(progressRepo).upsert(childId, lessonId, 50, false);

        service.updateProgress(childId, childId, lessonId, request(89, true));
        verify(progressRepo).upsert(childId, lessonId, 89, false);

        service.updateProgress(childId, childId, lessonId, request(90, true));
        verify(progressRepo).upsert(childId, lessonId, 90, true);

        // đã báo 95 giây trước đó thì lần báo "xong" sau với vị trí thấp hơn vẫn tính
        when(progressRepo.findByIdChildIdAndIdLessonId(childId, lessonId)).thenReturn(Optional.of(
                new ChildProgress(new ChildProgressId(childId, lessonId), 95, false)));
        service.updateProgress(childId, childId, lessonId, request(10, true));
        verify(progressRepo).upsert(childId, lessonId, 10, true);

        // không báo xong thì không bao giờ được đánh dấu
        service.updateProgress(childId, childId, lessonId, request(100, false));
        verify(progressRepo).upsert(childId, lessonId, 100, false);
    }

    @Test
    @DisplayName("hoàn thành: bài chưa có thời lượng (video cũ) thì không kiểm, giữ cách cũ")
    void unknownDurationIsNotChecked() {
        enroll("active");
        lesson.setDurationSeconds(null);
        service.updateProgress(childId, childId, lessonId, request(3, true));
        verify(progressRepo).upsert(childId, lessonId, 3, true);
    }

    @Test
    @DisplayName("ghi: bài học hoặc hồ sơ bé không tồn tại thì 404 thay vì lỗi khóa ngoại 500")
    void missingTargets() {
        UUID unknownLesson = UUID.randomUUID();
        when(lessonRepo.findById(unknownLesson)).thenReturn(Optional.empty());
        assertEquals("LESSON_NOT_FOUND", assertThrows(AppError.class,
                () -> service.updateProgress(childId, childId, unknownLesson, request(1, false))).getErrorCode());

        UUID notAChild = UUID.randomUUID();
        when(childRepo.findByUserId(notAChild)).thenReturn(Optional.empty());
        assertEquals("CHILD_NOT_FOUND", assertThrows(AppError.class,
                () -> service.updateProgress(notAChild, notAChild, lessonId, request(1, false))).getErrorCode());
    }

    @Test
    @DisplayName("tiến độ theo khóa: đếm bài đã xong trên tổng số bài của khóa")
    void courseProgress() {
        UUID l2 = UUID.randomUUID();
        UUID l3 = UUID.randomUUID();
        UUID otherCourseLesson = UUID.randomUUID();
        when(lessonRepo.findAllByCourseId(courseId)).thenReturn(List.of(
                lesson, Lesson.builder().id(l2).courseId(courseId).build(), Lesson.builder().id(l3).courseId(courseId).build()));
        when(progressRepo.findAllByIdChildId(childId)).thenReturn(List.of(
                new ChildProgress(new ChildProgressId(childId, lessonId), 100, true),
                new ChildProgress(new ChildProgressId(childId, l2), 20, false),
                new ChildProgress(new ChildProgressId(childId, otherCourseLesson), 100, true)));

        CourseProgressResponse response = service.getCourseProgress(parentId, childId, courseId);

        assertEquals(1, response.getCompletedLessons());
        assertEquals(3, response.getTotalLessons());
        assertEquals(33, response.getPercent());
    }
}
