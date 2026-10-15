import { Router } from 'express';
import { CoursesController } from './courses.controller';
import { requireAuth, requireRoles } from '../../middlewares/auth.middleware';

const router = Router();

// Công khai (SHOP-01)
router.get('/', CoursesController.listCourses);
router.get('/:courseId', CoursesController.getCourseDetail);

// Xem thử miễn phí yêu cầu Parent đã đăng nhập (SHOP-10)
router.get('/lessons/:lessonId/trial', requireAuth, requireRoles('PARENT'), CoursesController.getFreeTrialLesson);

// Đánh giá khóa học (SHOP-09: Phụ huynh đánh giá 1-5 sao)
router.post('/:courseId/reviews', requireAuth, requireRoles('PARENT'), CoursesController.createCourseReview);

export default router;
