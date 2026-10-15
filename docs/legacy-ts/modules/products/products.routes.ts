import { Router } from 'express';
import { ProductsController } from './products.controller';

const router = Router();

// Công khai duyệt dụng cụ vẽ (SHOP-01)
router.get('/', ProductsController.listSupplies);
router.get('/:supplyId', ProductsController.getSupplyDetail);
router.get('/lessons/:lessonId/supplies', ProductsController.getLessonSupplies);

export default router;
