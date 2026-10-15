import { Request, Response, NextFunction } from 'express';
import { CartService } from './cart.service';
import { AddToCartSchema, UpdateCartItemSchema } from './cart.validation';

export class CartController {
  static async getCart(req: Request, res: Response, next: NextFunction) {
    try {
      const parentId = req.user!.userId;
      const data = await CartService.getCart(parentId);
      res.json({ success: true, data });
    } catch (err) {
      next(err);
    }
  }

  static async addToCart(req: Request, res: Response, next: NextFunction) {
    try {
      const parentId = req.user!.userId;
      const validated = AddToCartSchema.parse(req.body);
      const result = await CartService.addToCart(parentId, validated);
      res.status(201).json(result);
    } catch (err) {
      next(err);
    }
  }

  static async updateQuantity(req: Request, res: Response, next: NextFunction) {
    try {
      const parentId = req.user!.userId;
      const { itemId } = req.params;
      const { quantity } = UpdateCartItemSchema.parse(req.body);
      const result = await CartService.updateQuantity(parentId, itemId, quantity);
      res.json(result);
    } catch (err) {
      next(err);
    }
  }

  static async removeItem(req: Request, res: Response, next: NextFunction) {
    try {
      const parentId = req.user!.userId;
      const { itemId } = req.params;
      const result = await CartService.removeItem(parentId, itemId);
      res.json(result);
    } catch (err) {
      next(err);
    }
  }
}
