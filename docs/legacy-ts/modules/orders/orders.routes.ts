import { Router } from 'express';
import { OrdersController } from './orders.controller';
import { requireAuth, requireRoles } from '../../middlewares/auth.middleware';

const router = Router();

// Yêu cầu xác thực (Child bị chặn -> 403)
router.use(requireAuth);

// Parent routes
router.post('/checkout', requireRoles('PARENT'), OrdersController.checkout);
router.get('/my-orders', requireRoles('PARENT'), OrdersController.getMyOrders);
router.post('/:orderId/cancel', requireRoles('PARENT'), OrdersController.cancelOrder);

// Teacher routes (FUL-01: Teacher xem đơn có dụng cụ của mình)
router.get('/teacher-orders', requireRoles('TEACHER'), OrdersController.getTeacherOrders);

// Chi tiết đơn hàng (Parent xem đơn của mình, Teacher xem đơn của mình)
router.get('/:orderId', OrdersController.getOrderDetail);

export default router;
