
package com.example.art_course.lesson.controller;

import com.example.art_course.lesson.dto.CreateLessonRequest;
import com.example.art_course.lesson.dto.LessonResponse;
import com.example.art_course.lesson.dto.UpdateLessonRequest;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.service.LessonService;
import com.example.art_course.course.service.CourseService;
import com.example.art_course.video.service.VideoAccessService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonService lessonService;
    private final CourseService courseService;
    private final VideoAccessService videoAccessService;

    public LessonController(LessonService lessonService, CourseService courseService,
                            VideoAccessService videoAccessService) {
        this.lessonService = lessonService;
        this.courseService = courseService;
        this.videoAccessService = videoAccessService;
    }

    @GetMapping("/course/{courseId}")
    public List<LessonResponse> getLessonsByCourseId(
            @PathVariable UUID courseId, Authentication authentication) {

        // CUS-10: khóa làm riêng chỉ hiện với giáo viên và phụ huynh của yêu cầu (cũng kiểm bản nháp).
        courseService.getCourseForViewer(courseId, authentication.getName());
        boolean full = canViewContent(courseId, authentication);

        return lessonService.getLessonsByCourseId(courseId)
                .stream()
                .map(lesson -> toResponse(lesson, full))
                .toList();
    }

    @GetMapping("/{lessonId}")
    public LessonResponse getLessonById(
            @PathVariable UUID lessonId, Authentication authentication) {
        Lesson lesson = lessonService.getLessonById(lessonId);
        courseService.getCourseForViewer(lesson.getCourseId(), authentication.getName());
        return toResponse(lesson, canViewContent(lesson.getCourseId(), authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LessonResponse createLesson(
            @Valid @RequestBody CreateLessonRequest request,
            Authentication authentication) {

        Lesson lesson = Lesson.builder()
                .courseId(request.getCourseId())
                .supplyId(request.getSupplyId())
                .title(request.getTitle())
                .videoUrl(request.getVideoUrl())
                .isTrial(request.getIsTrial())
                .hasAssignment(request.getHasAssignment())
                .assignmentTitle(request.getAssignmentTitle())
                .assignmentPrompt(request.getAssignmentPrompt())
                .status(request.getStatus())
                .build();

        Lesson savedLesson = lessonService.createLesson(
                lesson,
                authentication.getName()
        );

        return toResponse(savedLesson);
    }

    @PutMapping("/{lessonId}")
    public LessonResponse updateLesson(
            @PathVariable UUID lessonId,
            @Valid @RequestBody UpdateLessonRequest request,
            Authentication authentication) {

        return toResponse(
                lessonService.updateLesson(
                        lessonId,
                        request,
                        authentication.getName()
                )
        );
    }

    @DeleteMapping("/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(
            @PathVariable UUID lessonId,
            Authentication authentication) {

        lessonService.deleteLesson(
                lessonId,
                authentication.getName()
        );
    }

    // Quyền xem nội dung trả phí của khóa: xem VideoAccessService.canViewCourse.
    private boolean canViewContent(UUID courseId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            return false;
        }
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst().orElse(null);
        return videoAccessService.canViewCourse(courseId, userId, role);
    }

    // Người chưa có quyền (ví dụ phụ huynh đang xem trước khi mua) chỉ thấy phần để hiển thị danh sách bài:
    // không có đề bài tập, ảnh mẫu; đường dẫn video chỉ giữ ở bài xem thử (luồng video vẫn được kiểm quyền riêng).
    private LessonResponse toResponse(Lesson lesson, boolean full) {
        if (full) {
            return toResponse(lesson);
        }
        boolean trial = Boolean.TRUE.equals(lesson.getIsTrial());
        return new LessonResponse(
                lesson.getId(),
                lesson.getCourseId(),
                lesson.getSupplyId(),
                lesson.getTitle(),
                trial ? lesson.getVideoUrl() : null,
                lesson.getIsTrial(),
                lesson.getHasAssignment(),
                lesson.getStatus(),
                lesson.getVideoStatus(),
                lesson.getDurationSeconds(),
                null,
                null,
                null
        );
    }

    private LessonResponse toResponse(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getCourseId(),
                lesson.getSupplyId(),
                lesson.getTitle(),
                lesson.getVideoUrl(),
                lesson.getIsTrial(),
                lesson.getHasAssignment(),
                lesson.getStatus(),
                lesson.getVideoStatus(),
                lesson.getDurationSeconds(),
                lesson.getAssignmentTitle(),
                lesson.getAssignmentPrompt(),
                lesson.getSampleImageUrl()
        );
    }
}
