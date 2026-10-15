import { Router } from 'express';
import { CartController } from './cart.controller';
import { requireAuth, requireRoles } from '../../middlewares/auth.middleware';

const router = Router();

// Tất cả API giỏ hàng yêu cầu đăng nhập và chỉ dành cho Parent (Child bị chặn -> 403)
router.use(requireAuth, requireRoles('PARENT'));

router.get('/', CartController.getCart);
router.post('/items', CartController.addToCart);
router.put('/items/:itemId', CartController.updateQuantity);
router.delete('/items/:itemId', CartController.removeItem);

export default router;
