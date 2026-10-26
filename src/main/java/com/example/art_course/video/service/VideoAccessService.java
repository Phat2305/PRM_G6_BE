package com.example.art_course.video.service;

import com.example.art_course.child.entity.Child;
import com.example.art_course.child.repository.ChildRepository;
import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.lesson.entity.Lesson;
import com.example.art_course.lesson.repository.LessonRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ai được xem video HLS (LRN-03): trước đây chỉ cần đăng nhập và biết hlsId là xem được video trả phí.
 * Được xem khi: Staff/Admin (kiểm duyệt); giáo viên sở hữu khóa; bé đã được cấp quyền học khóa đó;
 * phụ huynh có bé đã được cấp quyền, hoặc là chủ yêu cầu của khóa riêng đã được Staff duyệt (xem trước khi nhận);
 * bài học xem thử (is_trial) thì mọi người đã đăng nhập.
 */
@Service
public class VideoAccessService {

    // Playlist và từng đoạn video đều đi qua cùng một endpoint nên nhớ ngắn hạn phép tra hlsId -> bài học.
    private static final long CACHE_MILLIS = 60_000;

    private record LessonRef(UUID courseId, boolean trial) {}

    private record Cached(List<LessonRef> refs, long expiresAt) {}

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final ChildRepository childRepository;
    private final CustomizeRequestRepository requestRepository;
    private final IEnrollmentContract enrollmentContract;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public VideoAccessService(
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            ChildRepository childRepository,
            CustomizeRequestRepository requestRepository,
            IEnrollmentContract enrollmentContract) {
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.childRepository = childRepository;
        this.requestRepository = requestRepository;
        this.enrollmentContract = enrollmentContract;
    }

    public boolean canStream(UUID hlsId, UUID userId, String role) {
        if (userId == null || role == null) {
            return false;
        }
        String r = role.trim().toUpperCase();
        if (r.equals("STAFF") || r.equals("ADMIN")) {
            return true;
        }
        for (LessonRef ref : refsOf(hlsId)) {
            if (ref.trial() || allowedForCourse(ref.courseId(), userId, r)) {
                return true;
            }
        }
        return false;
    }

    private boolean allowedForCourse(UUID courseId, UUID userId, String role) {
        switch (role) {
            case "TEACHER":
                return courseRepository.findById(courseId)
                        .map(c -> userId.equals(c.getTeacherId())).orElse(false);
            case "CHILD":
                return enrollmentContract.isEnrolled(userId, courseId);
            case "PARENT":
                for (Child child : childRepository.findAllByParentId(userId)) {
                    if (enrollmentContract.isEnrolled(child.getUserId(), courseId)) {
                        return true;
                    }
                }
                return ownsDeliveredCustomizeCourse(courseId, userId);
            default:
                return false;
        }
    }

    // Phụ huynh xem khóa riêng đã được Staff duyệt (yêu cầu DELIVERED/COMPLETED) trước khi nhận.
    private boolean ownsDeliveredCustomizeCourse(UUID courseId, UUID parentId) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null || course.getCustomizeRequestId() == null) {
            return false;
        }
        return requestRepository.findById(course.getCustomizeRequestId())
                .map(r -> parentId.equals(r.getParentId())
                        && ("DELIVERED".equalsIgnoreCase(r.getStatus()) || "COMPLETED".equalsIgnoreCase(r.getStatus())))
                .orElse(false);
    }

    private List<LessonRef> refsOf(UUID hlsId) {
        long now = System.currentTimeMillis();
        Cached cached = cache.get(hlsId);
        if (cached != null && cached.expiresAt() > now) {
            return cached.refs();
        }
        // Đường dẫn do pipeline đặt dạng /api/videos/hls/{hlsId}/master.m3u8 (VideoLessonService.completeLessonVideo).
        List<LessonRef> refs = lessonRepository.findAllByVideoUrlContaining("/hls/" + hlsId + "/").stream()
                .map((Lesson l) -> new LessonRef(l.getCourseId(), Boolean.TRUE.equals(l.getIsTrial())))
                .toList();
        cache.put(hlsId, new Cached(refs, now + CACHE_MILLIS));
        return refs;
    }
}
