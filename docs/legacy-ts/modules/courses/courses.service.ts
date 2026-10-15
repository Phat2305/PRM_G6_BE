import { query } from '../../config/db';
import { AppError } from '../../middlewares/error.middleware';
import { eventBus } from '../../contracts/events.contract';

export class CoursesService {
  /**
   * API duyệt/tìm kiếm khóa học (SHOP-01; đọc dữ liệu BE1)
   */
  static async listCourses(filters: { search?: string; category?: string }) {
    let sql = `
      SELECT c.id, c.name, c.description, c.category, c.cover_image_url, c.price, c.teacher_id,
             tp.full_name AS teacher_name
      FROM public.courses c
      LEFT JOIN public.teacher_profiles tp ON c.teacher_id = tp.user_id
      WHERE (c.status = 'PUBLISHED' OR c.status = 'published')
    `;
    const params: any[] = [];

    if (filters.search) {
      params.push(`%${filters.search}%`);
      sql += ` AND c.name ILIKE $${params.length}`;
    }
    if (filters.category) {
      params.push(filters.category);
      sql += ` AND c.category = $${params.length}`;
    }

    sql += ' ORDER BY c.created_at DESC';
    const res = await query(sql, params);
    return res.rows;
  }

  /**
   * Chi tiết khóa học kèm danh sách bài học và dụng cụ gợi ý (SHOP-01)
   */
  static async getCourseDetail(courseId: string) {
    const courseRes = await query(
      `SELECT c.*, tp.full_name AS teacher_name, tp.certificates_text
       FROM public.courses c
       LEFT JOIN public.teacher_profiles tp ON c.teacher_id = tp.user_id
       WHERE c.id = $1`,
      [courseId]
    );

    if (courseRes.rowCount === 0) {
      throw new AppError(404, 'COURSE_NOT_FOUND', 'Không tìm thấy khóa học');
    }

    // Danh sách bài học
    const lessonsRes = await query(
      `SELECT id, title, is_trial, has_assignment, status
       FROM public.lessons
       WHERE course_id = $1
       ORDER BY id ASC`,
      [courseId]
    );

    // Học cụ gợi ý theo bài học (SHOP-01, CRS-08, #15)
    const suppliesRes = await query(
      `SELECT s.id, s.name, s.price, s.image_url, (s.stock_quantity - s.reserved_quantity) AS available_stock,
              ls.lesson_id, l.title AS lesson_title
       FROM public.lesson_supplies ls
       JOIN public.lessons l ON ls.lesson_id = l.id
       JOIN public.supplies s ON ls.supply_id = s.id
       WHERE l.course_id = $1`,
      [courseId]
    );

    return {
      course: courseRes.rows[0],
      lessons: lessonsRes.rows,
      recommendedSupplies: suppliesRes.rows,
    };
  }

  /**
   * Xem thử bài học miễn phí (SHOP-10)
   * Chỉ cần Parent đã đăng nhập; không tạo tiến độ học tập (LRN-09).
   */
  static async getFreeTrialLesson(lessonId: string) {
    const res = await query(
      `SELECT l.id, l.course_id, l.title, l.video_url, l.is_trial
       FROM public.lessons l
       JOIN public.courses c ON l.course_id = c.id
       WHERE l.id = $1 AND (c.status = 'PUBLISHED' OR c.status = 'published')`,
      [lessonId]
    );

    if (res.rowCount === 0) {
      throw new AppError(404, 'LESSON_NOT_FOUND', 'Không tìm thấy bài học');
    }

    const lesson = res.rows[0];
    if (!lesson.is_trial) {
      throw new AppError(403, 'FORBIDDEN', 'Bài học này không cho phép xem thử miễn phí (SHOP-10)');
    }

    return {
      lessonId: lesson.id,
      courseId: lesson.course_id,
      title: lesson.title,
      videoUrl: lesson.video_url,
      isTrial: true,
      note: 'Xem thử miễn phí không lưu tiến độ học tập (LRN-09)',
    };
  }

  /**
   * Đánh giá khóa học (SHOP-09)
   * Điều kiện: Chỉ Parent có Child đã được gán khóa đó, 1-5 sao, chống đánh giá trùng.
   * Phát sự kiện "course_reviewed" cho module dòng tiền (mục 7.1).
   */
  static async createCourseReview(
    courseId: string,
    parentId: string,
    rating: number,
    comment?: string
  ) {
    if (rating < 1 || rating > 5) {
      throw new AppError(400, 'INVALID_RATING', 'Điểm đánh giá phải từ 1 đến 5 sao (SHOP-09)');
    }

    // 1. Kiểm tra Parent có con đã sở hữu khóa học này không
    const enrolledRes = await query(
      `SELECT cp.user_id AS child_id
       FROM public.child_enroll ce
       JOIN public.child_profiles cp ON ce.child_id = cp.user_id
       WHERE ce.course_id = $1 AND cp.parent_id = $2 AND ce.status = 'ACTIVE'
       LIMIT 1`,
      [courseId, parentId]
    );

    if (enrolledRes.rowCount === 0) {
      throw new AppError(403, 'NOT_ELIGIBLE', 'Chỉ phụ huynh có con đang theo học khóa này mới được gửi đánh giá (SHOP-09)');
    }

    const childId = enrolledRes.rows[0].child_id;

    // 2. Chống đánh giá trùng (mỗi phụ huynh 1 đánh giá cho 1 khóa học)
    const existingRes = await query(
      `SELECT 1 FROM public.course_reviews WHERE course_id = $1 AND parent_id = $2`,
      [courseId, parentId]
    );
    if (existingRes.rowCount && existingRes.rowCount > 0) {
      throw new AppError(409, 'ALREADY_REVIEWED', 'Bạn đã gửi đánh giá cho khóa học này rồi (SHOP-09)');
    }

    // 3. Lưu đánh giá
    const insertRes = await query(
      `INSERT INTO public.course_reviews (course_id, parent_id, child_id, rating, comment)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING id, rating, comment`,
      [courseId, parentId, childId, rating, comment || null]
    );

    // 4. Phát sự kiện "course_reviewed" cho module dòng tiền (mục 7.1)
    await eventBus.emit('course_reviewed', {
      courseId,
      parentId,
      childId,
      rating,
      comment,
    });

    return {
      success: true,
      message: 'Gửi đánh giá khóa học thành công',
      review: insertRes.rows[0],
    };
  }
}
