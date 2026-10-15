import { Request, Response, NextFunction } from 'express';
import { CoursesService } from './courses.service';

export class CoursesController {
  static async listCourses(req: Request, res: Response, next: NextFunction) {
    try {
      const search = req.query.search as string;
      const category = req.query.category as string;
      const courses = await CoursesService.listCourses({ search, category });
      res.json({ success: true, data: courses });
    } catch (err) {
      next(err);
    }
  }

  static async getCourseDetail(req: Request, res: Response, next: NextFunction) {
    try {
      const { courseId } = req.params;
      const data = await CoursesService.getCourseDetail(courseId);
      res.json({ success: true, data });
    } catch (err) {
      next(err);
    }
  }

  static async getFreeTrialLesson(req: Request, res: Response, next: NextFunction) {
    try {
      const { lessonId } = req.params;
      const lesson = await CoursesService.getFreeTrialLesson(lessonId);
      res.json({ success: true, data: lesson });
    } catch (err) {
      next(err);
    }
  }

  static async createCourseReview(req: Request, res: Response, next: NextFunction) {
    try {
      const { courseId } = req.params;
      const parentId = req.user!.userId;
      const { rating, comment } = req.body;
      const result = await CoursesService.createCourseReview(courseId, parentId, Number(rating), comment);
      res.status(201).json(result);
    } catch (err) {
      next(err);
    }
  }
}
