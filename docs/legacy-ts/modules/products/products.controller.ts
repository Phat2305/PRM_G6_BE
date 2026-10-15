import { Request, Response, NextFunction } from 'express';
import { ProductsService } from './products.service';

export class ProductsController {
  static async listSupplies(req: Request, res: Response, next: NextFunction) {
    try {
      const search = req.query.search as string;
      const supplies = await ProductsService.listSupplies({ search });
      res.json({ success: true, data: supplies });
    } catch (err) {
      next(err);
    }
  }

  static async getSupplyDetail(req: Request, res: Response, next: NextFunction) {
    try {
      const { supplyId } = req.params;
      const supply = await ProductsService.getSupplyDetail(supplyId);
      res.json({ success: true, data: supply });
    } catch (err) {
      next(err);
    }
  }

  static async getLessonSupplies(req: Request, res: Response, next: NextFunction) {
    try {
      const { lessonId } = req.params;
      const supplies = await ProductsService.getRecommendedSuppliesByLesson(lessonId);
      res.json({ success: true, data: supplies });
    } catch (err) {
      next(err);
    }
  }
}
