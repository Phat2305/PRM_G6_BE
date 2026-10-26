package com.example.art_course.modules.course.service;

import com.example.art_course.common.AppError;
import com.example.art_course.contracts.events.CourseReviewedEvent;
import com.example.art_course.modules.course.entity.Course;
import com.example.art_course.modules.course.entity.CourseReview;
import com.example.art_course.modules.course.entity.Lesson;
import com.example.art_course.modules.course.repository.CourseRepository;
import com.example.art_course.modules.course.repository.CourseReviewRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import com.example.art_course.contracts.IVideoAccessContract;
import com.example.art_course.modules.course.dto.CourseDetailDto;
import com.example.art_course.modules.course.dto.CourseSummaryDto;
import com.example.art_course.modules.course.dto.LessonSummaryDto;
import com.example.art_course.modules.course.dto.TrialLessonDto;

import java.util.Optional;

@Service("be2CourseService")
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final IVideoAccessContract videoAccessContract;
    private final com.example.art_course.common.TeacherNames teacherNames;

    /**
     * Danh mục khóa học đã xuất bản phục vụ mua sắm (SHOP-01).
     * Tuyệt đối không chứa video_url.
     */
    @Transactional(readOnly = true)
    public List<CourseSummaryDto> listCourses() {
        // CUS-10: khóa học làm riêng cho một yêu cầu customize không bán công khai
        List<Course> published = courseRepository.findByStatusIgnoreCase("PUBLISHED").stream()
                .filter(c -> c.getCustomizeRequestId() == null)
                .toList();
        java.util.Map<UUID, String> names = teacherNames.byIds(
                published.stream().map(Course::getTeacherId).distinct().toList());
        java.util.Map<UUID, CourseReviewRepository.RatingSummary> ratings = ratingsOf(
                published.stream().map(Course::getId).toList());
        return published.stream()
                .map(c -> CourseSummaryDto.builder()
                        .id(c.getId())
                        .teacherId(c.getTeacherId())
                        .teacherName(names.get(c.getTeacherId()))
                        .name(c.getName())
                        .description(c.getDescription())
                        .category(c.getCategory())
                        .coverImageUrl(c.getCoverImageUrl())
                        .price(c.getPrice())
                        .status(c.getStatus())
                        .averageRating(averageOf(ratings.get(c.getId())))
                        .reviewCount(countOf(ratings.get(c.getId())))
                        .build())
                .toList();
    }

    private java.util.Map<UUID, CourseReviewRepository.RatingSummary> ratingsOf(java.util.Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return java.util.Map.of();
        }
        return courseReviewRepository.summarize(courseIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        CourseReviewRepository.RatingSummary::getCourseId, s -> s));
    }

    private static Double averageOf(CourseReviewRepository.RatingSummary summary) {
        return summary == null || summary.getAverage() == null
                ? null
                : Math.round(summary.getAverage() * 10) / 10.0;
    }

    private static long countOf(CourseReviewRepository.RatingSummary summary) {
        return summary == null || summary.getTotal() == null ? 0 : summary.getTotal();
    }

    /**
     * Xem chi tiết khóa học khi chọn mua (SHOP-01).
     * Danh sách bài học chỉ trả tiêu đề và trạng thái, tuyệt đối không chứa video_url thô.
     */
    @Transactional(readOnly = true)
    public CourseDetailDto getCourseDetail(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getCustomizeRequestId() == null)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học"));

        List<Lesson> lessons = entityManager.createQuery(
                "SELECT l FROM Lesson l WHERE l.courseId = :courseId ORDER BY l.id ASC", Lesson.class)
                .setParameter("courseId", courseId)
                .getResultList();

        CourseReviewRepository.RatingSummary rating = ratingsOf(List.of(courseId)).get(courseId);

        List<LessonSummaryDto> lessonDtos = lessons.stream()
                .map(l -> LessonSummaryDto.builder()
                        .id(l.getId())
                        .title(l.getTitle())
                        .isTrial(l.isTrial())
                        .hasAssignment(l.isHasAssignment())
                        .status(l.getStatus())
                        .build())
                .toList();

        return CourseDetailDto.builder()
                .id(course.getId())
                .teacherId(course.getTeacherId())
                .teacherName(teacherNames.byId(course.getTeacherId()))
                .name(course.getName())
                .description(course.getDescription())
                .category(course.getCategory())
                .coverImageUrl(course.getCoverImageUrl())
                .price(course.getPrice())
                .status(course.getStatus())
                .averageRating(averageOf(rating))
                .reviewCount(countOf(rating))
                .lessons(lessonDtos)
                .build();
    }

    /**
     * Xem thử bài học miễn phí (SHOP-10).
     * Tuyệt đối không trả lessons.video_url thô. Gọi IVideoAccessContract sang BE1 để lấy signed URL (LRN-02/03).
     */
    @Transactional(readOnly = true)
    public TrialLessonDto getFreeTrialLesson(UUID lessonId) {
        Lesson lesson = entityManager.find(Lesson.class, lessonId);
        if (lesson == null) {
            throw new AppError(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Không tìm thấy bài học");
        }
        if (!lesson.isTrial()) {
            throw new AppError(HttpStatus.FORBIDDEN, "TRIAL_NOT_ALLOWED", "Bài học này không hỗ trợ xem thử miễn phí (SHOP-10)");
        }

        Optional<String> signedUrlOpt = videoAccessContract.getSignedTrialVideoUrl(lessonId);

        return TrialLessonDto.builder()
                .lessonId(lesson.getId())
                .courseId(lesson.getCourseId())
                .title(lesson.getTitle())
                .isTrial(lesson.isTrial())
                .hasAssignment(lesson.isHasAssignment())
                .signedVideoUrl(signedUrlOpt.orElse(null))
                .message(signedUrlOpt.isPresent() ? "Signed URL hợp lệ (SHOP-10)" : "Chờ BE1 cấp signed URL an toàn (LRN-02/03); không phát video_url thô")
                .build();
    }

    @Transactional
    public CourseReview createCourseReview(UUID courseId, UUID parentId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_RATING", "Đánh giá phải từ 1 đến 5 sao (SHOP-09)");
        }

        // 1. Kiểm tra con của phụ huynh đã sở hữu khóa học hay chưa
        String checkEnrollSql = """
            SELECT cp.user_id 
            FROM child_enroll ce
            JOIN child_profiles cp ON ce.child_id = cp.user_id
            WHERE ce.course_id = :courseId AND cp.parent_id = :parentId AND LOWER(ce.status) = 'active'
            LIMIT 1
        """;
        List<?> enrolled = entityManager.createNativeQuery(checkEnrollSql)
                .setParameter("courseId", courseId)
                .setParameter("parentId", parentId)
                .getResultList();

        if (enrolled.isEmpty()) {
            throw new AppError(HttpStatus.FORBIDDEN, "NOT_ELIGIBLE", "Chỉ phụ huynh có con đang học khóa này mới được đánh giá (SHOP-09)");
        }

        UUID childId = (UUID) enrolled.get(0);

        // 2. Chống đánh giá trùng
        if (courseReviewRepository.findByCourseIdAndParentId(courseId, parentId).isPresent()) {
            throw new AppError(HttpStatus.CONFLICT, "ALREADY_REVIEWED", "Bạn đã đánh giá khóa học này rồi (SHOP-09)");
        }

        // 3. Lưu đánh giá
        CourseReview review = CourseReview.builder()
                .courseId(courseId)
                .parentId(parentId)
                .childId(childId)
                .rating(rating)
                .comment(comment)
                .build();
        review = courseReviewRepository.save(review);

        // 4. Phát sự kiện CourseReviewedEvent cho module dòng tiền (mục 7.1)
        eventPublisher.publishEvent(new CourseReviewedEvent(this, courseId, parentId, childId, rating, comment));

        return review;
    }

    @Transactional(readOnly = true)
    public List<CourseReview> getCourseReviews(UUID courseId) {
        return courseReviewRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
    }

    // Giáo viên xem đánh giá khóa của chính mình; khóa của người khác hoặc không tồn tại đều bị từ chối như nhau.
    @Transactional(readOnly = true)
    public List<CourseReview> getCourseReviewsForTeacher(UUID courseId, UUID teacherId) {
        boolean owner = courseRepository.findById(courseId)
                .map(course -> teacherId != null && teacherId.equals(course.getTeacherId()))
                .orElse(false);
        if (!owner) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ giáo viên của khóa mới xem được đánh giá của khóa");
        }
        return courseReviewRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
    }
}
