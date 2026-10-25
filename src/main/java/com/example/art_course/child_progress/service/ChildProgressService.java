package com.example.art_course.child_progress.service;

import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.child_progress.dto.ChildProgressResponse;
import com.example.art_course.child_progress.dto.CourseProgressResponse;
import com.example.art_course.child_progress.dto.UpdateProgressRequest;
import com.example.art_course.child_progress.entity.ChildProgress;
import com.example.art_course.child_progress.repository.ChildProgressRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.enrollment.repository.EnrollmentRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChildProgressService {

    private final ChildProgressRepository progressRepository;
    private final ChildRepository childRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    public ChildProgressService(
            ChildProgressRepository progressRepository,
            ChildRepository childRepository,
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository) {
        this.progressRepository = progressRepository;
        this.childRepository = childRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(readOnly = true)
    public List<ChildProgressResponse> getByChildId(UUID callerId, UUID childId) {
        requireSelfOrParent(callerId, childId);
        return progressRepository.findAllByIdChildId(childId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChildProgressResponse getProgress(UUID callerId, UUID childId, UUID lessonId) {
        Lesson lesson = requireLesson(lessonId);
        requireCanRead(callerId, childId, lesson.getCourseId());

        ChildProgress progress = progressRepository
                .findByIdChildIdAndIdLessonId(childId, lessonId)
                .orElseThrow(() -> new AppError(
                        HttpStatus.NOT_FOUND,
                        "PROGRESS_NOT_FOUND",
                        "Bé chưa có tiến độ cho bài học này"));

        return toResponse(progress);
    }

    // Completed lessons out of all lessons of the course. Lessons the child has not opened count as not completed.
    @Transactional(readOnly = true)
    public CourseProgressResponse getCourseProgress(UUID callerId, UUID childId, UUID courseId) {
        requireCanRead(callerId, childId, courseId);

        Set<UUID> lessonIds = lessonRepository.findAllByCourseId(courseId).stream()
                .map(Lesson::getId)
                .collect(Collectors.toSet());
        int completed = (int) progressRepository.findAllByIdChildId(childId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                .filter(p -> lessonIds.contains(p.getId().getLessonId()))
                .count();
        int total = lessonIds.size();

        return CourseProgressResponse.builder()
                .childId(childId)
                .courseId(courseId)
                .completedLessons(completed)
                .totalLessons(total)
                .percent(total == 0 ? 0 : completed * 100 / total)
                .build();
    }

    // Teacher's view of one course: every enrolled child with completed/total lessons.
    // Only the owner of the course; children appear by id because their names belong to the parent.
    @Transactional(readOnly = true)
    public List<CourseProgressResponse> getCourseRoster(UUID callerId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học"));
        if (!callerId.equals(course.getTeacherId())) {
            throw forbidden("Chỉ giáo viên của khóa mới xem được tiến độ học viên");
        }

        Set<UUID> lessonIds = lessonRepository.findAllByCourseId(courseId).stream()
                .map(Lesson::getId)
                .collect(Collectors.toSet());
        Map<UUID, Long> completedByChild = lessonIds.isEmpty()
                ? Map.of()
                : progressRepository.findAllByIdLessonIdIn(lessonIds).stream()
                        .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                        .collect(Collectors.groupingBy(p -> p.getId().getChildId(), Collectors.counting()));
        int total = lessonIds.size();

        return enrollmentRepository.findAllByIdCourseId(courseId).stream()
                .filter(e -> "active".equalsIgnoreCase(e.getStatus()))
                .map(e -> e.getId().getChildId())
                .map(childId -> {
                    int completed = completedByChild.getOrDefault(childId, 0L).intValue();
                    return CourseProgressResponse.builder()
                            .childId(childId)
                            .courseId(courseId)
                            .completedLessons(completed)
                            .totalLessons(total)
                            .percent(total == 0 ? 0 : completed * 100 / total)
                            .build();
                })
                .toList();
    }

    // Only the child records their own progress, and only for a lesson they may watch:
    // a free trial lesson, or any lesson of a course they are enrolled in.
    @Transactional
    public ChildProgressResponse updateProgress(
            UUID callerId,
            UUID childId,
            UUID lessonId,
            UpdateProgressRequest request) {

        if (!callerId.equals(childId)) {
            throw forbidden("Chỉ bé mới ghi được tiến độ học của mình");
        }
        if (childRepository.findByUserId(childId).isEmpty()) {
            throw new AppError(HttpStatus.NOT_FOUND, "CHILD_NOT_FOUND", "Không tìm thấy hồ sơ của bé");
        }
        Lesson lesson = requireLesson(lessonId);
        if (!Boolean.TRUE.equals(lesson.getIsTrial()) && !isEnrolled(childId, lesson.getCourseId())) {
            throw new AppError(HttpStatus.FORBIDDEN, "NOT_ENROLLED", "Bé chưa được học khóa này");
        }

        // "Completed" needs the child to have reached most of the video, when its length is known.
        // Otherwise only the watched seconds are saved and the response shows the lesson as not completed.
        boolean completed = Boolean.TRUE.equals(request.getIsCompleted())
                && watchedEnough(childId, lesson, request.getWatchedSeconds());

        progressRepository.upsert(
                childId,
                lessonId,
                request.getWatchedSeconds(),
                completed);

        return toResponse(progressRepository
                .findByIdChildIdAndIdLessonId(childId, lessonId)
                .orElseThrow());
    }

    // At least 90% of the video, counting what was reported before. Lessons without a known length are not checked.
    private boolean watchedEnough(UUID childId, Lesson lesson, int reportedSeconds) {
        Integer duration = lesson.getDurationSeconds();
        if (duration == null || duration <= 0) {
            return true;
        }
        int before = progressRepository
                .findByIdChildIdAndIdLessonId(childId, lesson.getId())
                .map(ChildProgress::getWatchedSeconds)
                .orElse(0);
        return Math.max(before, reportedSeconds) * 10L >= duration * 9L;
    }

    // The child, or the parent who owns the child.
    private void requireSelfOrParent(UUID callerId, UUID childId) {
        if (callerId.equals(childId)
                || childRepository.findByUserIdAndParentId(childId, callerId).isPresent()) {
            return;
        }
        throw forbidden("Bạn không có quyền xem tiến độ của bé này");
    }

    // Same as above, plus the teacher who owns the course.
    private void requireCanRead(UUID callerId, UUID childId, UUID courseId) {
        if (callerId.equals(childId)
                || childRepository.findByUserIdAndParentId(childId, callerId).isPresent()
                || courseRepository.findById(courseId)
                        .map(course -> callerId.equals(course.getTeacherId()))
                        .orElse(false)) {
            return;
        }
        throw forbidden("Bạn không có quyền xem tiến độ của bé này");
    }

    private boolean isEnrolled(UUID childId, UUID courseId) {
        return courseId != null && enrollmentRepository
                .findByIdChildIdAndIdCourseId(childId, courseId)
                .map(e -> "active".equalsIgnoreCase(e.getStatus()))
                .orElse(false);
    }

    private Lesson requireLesson(UUID lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Không tìm thấy bài học"));
    }

    private AppError forbidden(String message) {
        return new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private ChildProgressResponse toResponse(ChildProgress progress) {
        return ChildProgressResponse.builder()
                .childId(progress.getId().getChildId())
                .lessonId(progress.getId().getLessonId())
                .watchedSeconds(progress.getWatchedSeconds())
                .isCompleted(progress.getIsCompleted())
                .build();
    }
}
