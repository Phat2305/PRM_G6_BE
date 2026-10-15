import { query, getClient } from '../../config/db';
import { AppError } from '../../middlewares/error.middleware';

export interface AddToCartInput {
  itemType: 'COURSE' | 'SUPPLY';
  courseId?: string;
  supplyId?: string;
  childId?: string;
  quantity?: number;
}

export class CartService {
  static async getOrCreateCart(parentId: string): Promise<string> {
    const res = await query(
      `INSERT INTO public.carts (parent_id)
       VALUES ($1)
       ON CONFLICT (parent_id) DO UPDATE SET updated_at = CURRENT_TIMESTAMP
       RETURNING id`,
      [parentId]
    );
    return res.rows[0].id;
  }

  static async getCart(parentId: string) {
    const cartId = await this.getOrCreateCart(parentId);

    const itemsQuery = `
      SELECT 
        ci.id,
        ci.item_type,
        ci.quantity,
        ci.child_id,
        cp.display_name AS child_name,
        c.id AS course_id,
        c.name AS course_name,
        c.price AS course_price,
        c.teacher_id AS course_teacher_id,
        tp_course.full_name AS course_teacher_name,
        s.id AS supply_id,
        s.name AS supply_name,
        s.price AS supply_price,
        (s.stock_quantity - s.reserved_quantity) AS available_stock,
        s.teacher_id AS supply_teacher_id,
        tp_supply.full_name AS supply_teacher_name
      FROM public.cart_items ci
      LEFT JOIN public.child_profiles cp ON ci.child_id = cp.user_id
      LEFT JOIN public.courses c ON ci.course_id = c.id
      LEFT JOIN public.teacher_profiles tp_course ON c.teacher_id = tp_course.user_id
      LEFT JOIN public.supplies s ON ci.supply_id = s.id
      LEFT JOIN public.teacher_profiles tp_supply ON s.teacher_id = tp_supply.user_id
      WHERE ci.cart_id = $1
      ORDER BY ci.created_at ASC
    `;

    const res = await query(itemsQuery, [cartId]);
    return {
      cartId,
      items: res.rows,
    };
  }

  static async addToCart(parentId: string, input: AddToCartInput) {
    const cartId = await this.getOrCreateCart(parentId);
    const client = await getClient();

    try {
      await client.query('BEGIN');

      if (input.itemType === 'COURSE') {
        // 1. Kiểm tra Parent có ít nhất 1 Child (ACC-04)
        const childCountRes = await client.query(
          'SELECT 1 FROM public.child_profiles WHERE parent_id = $1 LIMIT 1',
          [parentId]
        );
        if (childCountRes.rowCount === 0) {
          throw new AppError(400, 'NO_CHILD_PROFILE', 'Bạn cần tạo hồ sơ con (Child) trước khi mua khóa học (ACC-04)');
        }

        // 2. Kiểm tra Child có thuộc Parent này không
        const childRes = await client.query(
          'SELECT 1 FROM public.child_profiles WHERE user_id = $1 AND parent_id = $2',
          [input.childId, parentId]
        );
        if (childRes.rowCount === 0) {
          throw new AppError(403, 'FORBIDDEN', 'Hồ sơ con không thuộc quyền quản lý của bạn');
        }

        // 3. Kiểm tra khóa học có công khai không
        const courseRes = await client.query(
          'SELECT id, name, price, status FROM public.courses WHERE id = $1',
          [input.courseId]
        );
        if (courseRes.rowCount === 0) {
          throw new AppError(404, 'COURSE_NOT_FOUND', 'Không tìm thấy khóa học');
        }
        const course = courseRes.rows[0];
        if (course.status !== 'PUBLISHED' && course.status !== 'published') {
          throw new AppError(400, 'COURSE_NOT_PUBLISHED', 'Khóa học này hiện chưa được công khai');
        }

        // 4. Chặn mua trùng khóa học cho cùng 1 Child (SHOP-04)
        // Kiểm tra trong child_enroll
        const enrolledRes = await client.query(
          `SELECT 1 FROM public.child_enroll WHERE child_id = $1 AND course_id = $2 AND status = 'ACTIVE'`,
          [input.childId, input.courseId]
        );
        if (enrolledRes.rowCount && enrolledRes.rowCount > 0) {
          throw new AppError(409, 'COURSE_ALREADY_OWNED', 'Bé đã sở hữu khóa học này, không thể mua lại (SHOP-04)');
        }

        // Kiểm tra trong giỏ hàng hiện tại
        const inCartRes = await client.query(
          `SELECT 1 FROM public.cart_items 
           WHERE cart_id = $1 AND course_id = $2 AND child_id = $3`,
          [cartId, input.courseId, input.childId]
        );
        if (inCartRes.rowCount && inCartRes.rowCount > 0) {
          throw new AppError(409, 'COURSE_ALREADY_IN_CART', 'Khóa học này cho bé đã có trong giỏ hàng (SHOP-04)');
        }

        // Chèn vào giỏ
        await client.query(
          `INSERT INTO public.cart_items (cart_id, product_type, course_id, child_id, quantity)
           VALUES ($1, 'COURSE', $2, $3, 1)`,
          [cartId, input.courseId, input.childId]
        );
      } else {
        // Dụng cụ vẽ
        const qty = input.quantity || 1;

        const supplyRes = await client.query(
          'SELECT id, name, price, stock_quantity, reserved_quantity, status FROM public.supplies WHERE id = $1',
          [input.supplyId]
        );
        if (supplyRes.rowCount === 0) {
          throw new AppError(404, 'SUPPLY_NOT_FOUND', 'Không tìm thấy dụng cụ vẽ');
        }
        const supply = supplyRes.rows[0];
        if (supply.status !== 'ACTIVE' && supply.status !== 'active') {
          throw new AppError(400, 'SUPPLY_INACTIVE', 'Dụng cụ vẽ hiện không mở bán');
        }

        const available = supply.stock_quantity - (supply.reserved_quantity || 0);

        // Kiểm tra xem dụng cụ đã có trong giỏ chưa
        const existingRes = await client.query(
          `SELECT id, quantity FROM public.cart_items WHERE cart_id = $1 AND supply_id = $2`,
          [cartId, input.supplyId]
        );

        if (existingRes.rowCount && existingRes.rowCount > 0) {
          const newQty = existingRes.rows[0].quantity + qty;
          if (available < newQty) {
            throw new AppError(400, 'INSUFFICIENT_STOCK', `Số lượng tồn khả dụng không đủ (còn ${available}, yêu cầu ${newQty})`);
          }
          await client.query(
            'UPDATE public.cart_items SET quantity = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
            [newQty, existingRes.rows[0].id]
          );
        } else {
          if (available < qty) {
            throw new AppError(400, 'INSUFFICIENT_STOCK', `Số lượng tồn khả dụng không đủ (còn ${available}, yêu cầu ${qty})`);
          }
          await client.query(
            `INSERT INTO public.cart_items (cart_id, product_type, supply_id, quantity)
             VALUES ($1, 'SUPPLY', $2, $3)`,
            [cartId, input.supplyId, qty]
          );
        }
      }

      await client.query('COMMIT');
      return { success: true, message: 'Đã thêm sản phẩm vào giỏ hàng thành công' };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  static async updateQuantity(parentId: string, itemId: string, quantity: number) {
    const cartId = await this.getOrCreateCart(parentId);
    const itemRes = await query(
      `SELECT ci.*, s.stock_quantity, s.reserved_quantity 
       FROM public.cart_items ci
       LEFT JOIN public.supplies s ON ci.supply_id = s.id
       WHERE ci.id = $1 AND ci.cart_id = $2`,
      [itemId, cartId]
    );

    if (itemRes.rowCount === 0) {
      throw new AppError(404, 'NOT_FOUND', 'Không tìm thấy sản phẩm trong giỏ');
    }

    const item = itemRes.rows[0];
    if (item.product_type === 'COURSE') {
      throw new AppError(400, 'BAD_REQUEST', 'Khóa học chỉ có thể có số lượng là 1');
    }

    const available = item.stock_quantity - (item.reserved_quantity || 0);
    if (available < quantity) {
      throw new AppError(400, 'INSUFFICIENT_STOCK', `Tồn kho không đủ (còn ${available}, yêu cầu ${quantity})`);
    }

    await query(
      'UPDATE public.cart_items SET quantity = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
      [quantity, itemId]
    );

    return { success: true, message: 'Đã cập nhật số lượng' };
  }

  static async removeItem(parentId: string, itemId: string) {
    const cartId = await this.getOrCreateCart(parentId);
    const res = await query(
      'DELETE FROM public.cart_items WHERE id = $1 AND cart_id = $2 RETURNING id',
      [itemId, cartId]
    );
    if (res.rowCount === 0) {
      throw new AppError(404, 'NOT_FOUND', 'Không tìm thấy sản phẩm để xóa');
    }
    return { success: true, message: 'Đã xóa sản phẩm khỏi giỏ hàng' };
  }

  static async clearCart(cartId: string, client?: any) {
    const db = client || { query };
    await db.query('DELETE FROM public.cart_items WHERE cart_id = $1', [cartId]);
  }
}
