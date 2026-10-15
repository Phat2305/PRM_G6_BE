import { Request, Response, NextFunction } from 'express';
import { OrdersService } from './orders.service';
import { CheckoutSchema } from './orders.validation';

export class OrdersController {
  static async checkout(req: Request, res: Response, next: NextFunction) {
    try {
      const parentId = req.user!.userId;
      const validated = CheckoutSchema.parse(req.body);
      const result = await OrdersService.createOrdersFromCheckout(parentId, validated);
      res.status(201).json(result);
    } catch (err) {
      next(err);
    }
  }

  static async getMyOrders(req: Request, res: Response, next: NextFunction) {
    try {
      const parentId = req.user!.userId;
      const orders = await OrdersService.getParentOrders(parentId);
      res.json({ success: true, data: orders });
    } catch (err) {
      next(err);
    }
  }

  static async getOrderDetail(req: Request, res: Response, next: NextFunction) {
    try {
      const { orderId } = req.params;
      const userId = req.user!.userId;
      const userRole = req.user!.role;
      const order = await OrdersService.getOrderDetail(orderId, userId, userRole);
      res.json({ success: true, data: order });
    } catch (err) {
      next(err);
    }
  }

  static async getTeacherOrders(req: Request, res: Response, next: NextFunction) {
    try {
      const teacherId = req.user!.userId;
      const orders = await OrdersService.getTeacherOrders(teacherId);
      res.json({ success: true, data: orders });
    } catch (err) {
      next(err);
    }
  }

  static async cancelOrder(req: Request, res: Response, next: NextFunction) {
    try {
      const { orderId } = req.params;
      const parentId = req.user!.userId;
      const result = await OrdersService.cancelOrder(orderId, parentId);
      res.json(result);
    } catch (err) {
      next(err);
    }
  }
}
