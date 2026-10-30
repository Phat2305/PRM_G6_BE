package com.example.art_course.submission;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.enrollment.repository.EnrollmentRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import com.example.art_course.submission.entity.Submission;
import com.example.art_course.submission.repository.SubmissionRepository;
import com.example.art_course.submission.service.SubmissionService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import com.example.art_course.video.service.R2StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Bài nộp: chỉ bé đã có quyền học nộp ảnh, ảnh chỉ người liên quan xem, tên bé theo shareInfo")
class SubmissionServiceTest {

    private final SubmissionRepository submissionRepo = mock(SubmissionRepository.class);
    private final ChildRepository childRepo = mock(ChildRepository.class);
    private final LessonRepository lessonRepo = mock(LessonRepository.class);
    private final CourseRepository courseRepo = mock(CourseRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final EnrollmentRepository enrollRepo = mock(EnrollmentRepository.class);
    private final R2StorageService r2 = mock(R2StorageService.class);
    private final SubmissionService service = new SubmissionService(
            submissionRepo, childRepo, lessonRepo, courseRepo, userRepo, enrollRepo, r2);

    private final UUID childId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();
    private final MockMultipartFile png =
            new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});

    private User user(UUID id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        when(userRepo.findByPrincipal(id.toString())).thenReturn(Optional.of(u));
        return u;
    }

    @BeforeEach
    void setUp() {
        user(childId, "CHILD");
        user(parentId, "PARENT");
        user(teacherId, "TEACHER");
        user(strangerId, "PARENT");
        Child child = Child.builder().userId(childId).parentId(parentId).displayName("Bé Na").build();
        when(childRepo.findByUserId(childId)).thenReturn(Optional.of(child));
        when(childRepo.findById(childId)).thenReturn(Optional.of(child));
        when(childRepo.findAllById(any())).thenReturn(java.util.List.of(child));
        Lesson lesson = new Lesson();
        lesson.setCourseId(courseId);
        when(lessonRepo.findById(lessonId)).thenReturn(Optional.of(lesson));
        Course course = mock(Course.class);
        when(course.getTeacherId()).thenReturn(teacherId);
        when(courseRepo.findById(courseId)).thenReturn(Optional.of(course));
        when(submissionRepo.save(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(submissionId);
            }
            return s;
        });
        Submission stored = Submission.builder().id(submissionId).childId(childId).lessonId(lessonId)
                .imageUrl("x").build();
        when(submissionRepo.findById(submissionId)).thenReturn(Optional.of(stored));
    }

    private static void assertForbidden(Runnable action) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(403, e.getStatusCode().value());
    }

    @Test
    @DisplayName("nộp ảnh: chưa có quyền học → 403; có quyền thì lưu R2 và trả đường dẫn ảnh")
    void uploadNeedsEnrollment() {
        assertForbidden(() -> service.upload(lessonId, png, childId.toString()));
        verifyNoInteractions(r2);

        when(enrollRepo.existsByIdChildIdAndIdCourseIdAndStatusIgnoreCase(childId, courseId, "active")).thenReturn(true);
        var response = service.upload(lessonId, png, childId.toString());

        assertEquals("/api/submissions/" + submissionId + "/image", response.getImageUrl());
        assertEquals("Bé Na", response.getChildName());
        verify(r2).uploadBytes(eq("submissions/" + submissionId), any(), eq("image/png"));
    }

    @Test
    @DisplayName("nộp ảnh: phụ huynh không nộp thay; file sai định dạng bị từ chối")
    void uploadValidatesCallerAndFile() {
        assertForbidden(() -> service.upload(lessonId, png, parentId.toString()));
        when(enrollRepo.existsByIdChildIdAndIdCourseIdAndStatusIgnoreCase(childId, courseId, "active")).thenReturn(true);
        var gif = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1});
        AppError error = assertThrows(AppError.class,
                () -> service.upload(lessonId, gif, childId.toString()));
        assertEquals(400, error.getStatus().value());
    }

    @Test
    @DisplayName("xem ảnh: bé, phụ huynh, giáo viên của khóa được; người lạ → 403")
    void imageAccessIsScoped() {
        service.image(submissionId, childId.toString());
        service.image(submissionId, parentId.toString());
        service.image(submissionId, teacherId.toString());
        assertForbidden(() -> service.image(submissionId, strangerId.toString()));
    }

    @Test
    @DisplayName("tên bé: giáo viên chỉ thấy khi shareInfo bật; phụ huynh luôn thấy")
    void teacherSeesNameOnlyWhenShared() {
        Child child = Child.builder().userId(childId).parentId(parentId).displayName("Bé Na").build();
        User teacher = user(teacherId, "TEACHER");
        User parent = user(parentId, "PARENT");

        assertNull(SubmissionService.visibleName(child, teacher));
        assertEquals("Bé Na", SubmissionService.visibleName(child, parent));
        child.setShareInfo(true);
        assertEquals("Bé Na", SubmissionService.visibleName(child, teacher));
    }
}
