package com.example.art_course.enrollment;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.enrollment.controller.EnrollmentController;
import com.example.art_course.enrollment.dto.UpdateEnrollmentStatusRequest;
import com.example.art_course.enrollment.entity.Enrollment;
import com.example.art_course.enrollment.entity.EnrollmentId;
import com.example.art_course.enrollment.repository.EnrollmentRepository;
import com.example.art_course.enrollment.service.EnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Quyền học: không có endpoint tự cấp; chỉ bé, phụ huynh, giáo viên của khóa và Staff/Admin truy cập")
class EnrollmentAccessTest {

    private final EnrollmentRepository enrollRepo = mock(EnrollmentRepository.class);
    private final ChildRepository childRepo = mock(ChildRepository.class);
    private final CourseRepository courseRepo = mock(CourseRepository.class);
    private final EnrollmentService service = new EnrollmentService(enrollRepo, childRepo, courseRepo);

    private final UUID childId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Child child = Child.builder().userId(childId).parentId(parentId).displayName("Bé Na").build();
        when(childRepo.findByUserIdAndParentId(childId, parentId)).thenReturn(Optional.of(child));
        Course course = mock(Course.class);
        when(course.getTeacherId()).thenReturn(teacherId);
        when(courseRepo.findById(courseId)).thenReturn(Optional.of(course));
        Enrollment enrollment = new Enrollment(new EnrollmentId(childId, courseId), "active");
        when(enrollRepo.findAllByIdChildId(childId)).thenReturn(List.of(enrollment));
        when(enrollRepo.findAllByIdCourseId(courseId)).thenReturn(List.of(enrollment));
        when(enrollRepo.findByIdChildIdAndIdCourseId(childId, courseId)).thenReturn(Optional.of(enrollment));
    }

    private static void assertForbidden(Runnable action) {
        AppError error = assertThrows(AppError.class, action::run);
        assertEquals(403, error.getStatus().value());
    }

    @Test
    @DisplayName("xem theo bé: bé và phụ huynh của bé được; người lạ và giáo viên bị 403")
    void childEnrollmentsAreScoped() {
        assertEquals(1, service.getByChildId(childId, childId).size());
        assertEquals(1, service.getByChildId(parentId, childId).size());
        assertNotNull(service.getById(parentId, childId, courseId));
        assertForbidden(() -> service.getByChildId(stranger, childId));
        assertForbidden(() -> service.getByChildId(teacherId, childId));
        assertForbidden(() -> service.getById(stranger, childId, courseId));
    }

    @Test
    @DisplayName("xem theo khóa: chỉ giáo viên của khóa; phụ huynh, người lạ và khóa không tồn tại đều 403")
    void courseRosterOnlyForOwner() {
        assertEquals(1, service.getByCourseId(teacherId, courseId).size());
        assertForbidden(() -> service.getByCourseId(parentId, courseId));
        assertForbidden(() -> service.getByCourseId(stranger, courseId));
        assertForbidden(() -> service.getByCourseId(teacherId, UUID.randomUUID()));
    }

    @Test
    @DisplayName("không còn endpoint tạo quyền học; đổi trạng thái chỉ Staff/Admin")
    void controllerExposesNoGrantAndRestrictsStatus() throws Exception {
        assertTrue(Arrays.stream(EnrollmentController.class.getDeclaredMethods())
                .noneMatch(m -> m.isAnnotationPresent(PostMapping.class)));

        Method update = EnrollmentController.class.getMethod("updateStatus",
                UUID.class, UUID.class, UpdateEnrollmentStatusRequest.class);
        PreAuthorize guard = update.getAnnotation(PreAuthorize.class);
        assertNotNull(guard);
        assertTrue(guard.value().contains("STAFF") && guard.value().contains("ADMIN"));
        assertFalse(guard.value().contains("PARENT") || guard.value().contains("TEACHER"));
    }
}
