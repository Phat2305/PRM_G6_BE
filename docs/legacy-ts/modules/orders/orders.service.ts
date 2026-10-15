import { getClient, query } from '../../config/db';
import { AppError } from '../../middlewares/error.middleware';
import { Money } from '../../common/money';
import { ConfigService } from '../system-config/config.service';
import { CartService } from '../cart/cart.service';

export interface CheckoutInput {
  useCart?: boolean;
  items?: Array<{
    itemType: 'COURSE' | 'SUPPLY';
    courseId?: string;
    supplyId?: string;
    childId?: string;
    quantity?: number;
  }>;
  recipientName?: string;
  recipientPhone?: string;
  shippingAddress?: string;
}

export class OrdersService {
  /**
   * Tạo đơn hàng từ giỏ hoặc danh sách sản phẩm.
   * Tự động tách theo từng Teacher: mỗi Teacher một đơn riêng (SHOP-05, BR-04, #25).
   * Khóa dòng (FOR UPDATE) chống bán vượt tồn và ghi nhận giữ chỗ tồn kho (Quy tắc #2, PAY-07, BR-11).
   */
  static async createOrdersFromCheckout(parentId: string, input: CheckoutInput) {
    const client = await getClient();
    try {
      await client.query('BEGIN');

      const config = await ConfigService.getConfig();
      let rawItems: any[] = [];
      let cartId: string | null = null;

      if (input.useCart) {
        cartId = await CartService.getOrCreateCart(parentId);
        const cartItemsRes = await client.query(
          `SELECT item_type, course_id, supply_id, child_id, quantity
           FROM public.cart_items
           WHERE cart_id = $1`,
          [cartId]
        );
        if (cartItemsRes.rowCount === 0) {
          throw new AppError(400, 'CART_EMPTY', 'Giỏ hàng đang trống');
        }
        rawItems = cartItemsRes.rows.map((r: any) => ({
          itemType: r.item_type,
          courseId: r.course_id,
          supplyId: r.supply_id,
          childId: r.child_id,
          quantity: r.quantity,
        }));
      } else {
        if (!input.items || input.items.length === 0) {
          throw new AppError(400, 'NO_ITEMS', 'Không có sản phẩm nào được chọn để đặt hàng');
        }
        rawItems = input.items;
      }

      // Nhóm sản phẩm theo Teacher
      const teacherGroups = new Map<string, Array<{
        itemType: 'COURSE' | 'SUPPLY';
        itemId: string;
        title: string;
        unitPrice: Money;
        quantity: number;
        childId?: string;
      }>>();

      let hasAnySupply = false;

      for (const it of rawItems) {
        if (it.itemType === 'COURSE') {
          if (!it.courseId || !it.childId) {
            throw new AppError(400, 'BAD_REQUEST', 'Mục khóa học phải có courseId và childId (SHOP-03)');
          }

          // Chặn mua trùng khóa học cho cùng 1 Child (SHOP-04)
          const enrolled = await client.query(
            `SELECT 1 FROM public.child_enroll WHERE child_id = $1 AND course_id = $2 AND status = 'ACTIVE'`,
            [it.childId, it.courseId]
          );
          if (enrolled.rowCount && enrolled.rowCount > 0) {
            throw new AppError(409, 'COURSE_ALREADY_ENROLLED', 'Bé đã sở hữu khóa học này (SHOP-04)');
          }

          const cRes = await client.query(
            `SELECT id, name, price, teacher_id, status FROM public.courses WHERE id = $1`,
            [it.courseId]
          );
          if (cRes.rowCount === 0) {
            throw new AppError(404, 'NOT_FOUND', `Không tìm thấy khóa học ${it.courseId}`);
          }
          const course = cRes.rows[0];
          if (course.status !== 'PUBLISHED' && course.status !== 'published') {
            throw new AppError(400, 'COURSE_UNAVAILABLE', `Khóa học "${course.name}" chưa sẵn sàng mở bán`);
          }

          const teacherId = course.teacher_id;
          if (!teacherGroups.has(teacherId)) {
            teacherGroups.set(teacherId, []);
          }
          teacherGroups.get(teacherId)!.push({
            itemType: 'COURSE',
            itemId: course.id,
            title: course.name,
            unitPrice: new Money(course.price),
            quantity: 1,
            childId: it.childId,
          });
        } else if (it.itemType === 'SUPPLY') {
          hasAnySupply = true;
          if (!it.supplyId) {
            throw new AppError(400, 'BAD_REQUEST', 'Mục dụng cụ phải có supplyId');
          }
          const qty = it.quantity || 1;

          // Khóa dòng FOR UPDATE để kiểm tra tồn kho chính xác chống race condition (Quy tắc #2, PAY-07)
          const sRes = await client.query(
            `SELECT id, name, price, stock_quantity, reserved_quantity, teacher_id, status
             FROM public.supplies
             WHERE id = $1
             FOR UPDATE`,
            [it.supplyId]
          );
          if (sRes.rowCount === 0) {
            throw new AppError(404, 'NOT_FOUND', `Không tìm thấy dụng cụ ${it.supplyId}`);
          }
          const supply = sRes.rows[0];
          if (supply.status !== 'ACTIVE' && supply.status !== 'active') {
            throw new AppError(400, 'PRODUCT_UNAVAILABLE', `Dụng cụ "${supply.name}" không khả dụng`);
          }

          const availableStock = supply.stock_quantity - (supply.reserved_quantity || 0);
          if (availableStock < qty) {
            throw new AppError(
              400,
              'INSUFFICIENT_STOCK',
              `Dụng cụ "${supply.name}" không đủ tồn kho (còn ${availableStock}, yêu cầu ${qty}) (PAY-07)`
            );
          }

          // Cập nhật tăng tồn kho giữ chỗ (PAY-07, BR-11)
          await client.query(
            `UPDATE public.supplies 
             SET reserved_quantity = reserved_quantity + $1
             WHERE id = $2`,
            [qty, supply.id]
          );

          const teacherId = supply.teacher_id;
          if (!teacherGroups.has(teacherId)) {
            teacherGroups.set(teacherId, []);
          }
          teacherGroups.get(teacherId)!.push({
            itemType: 'SUPPLY',
            itemId: supply.id,
            title: supply.name,
            unitPrice: new Money(supply.price),
            quantity: qty,
          });
        }
      }

      if (hasAnySupply) {
        if (!input.shippingAddress || !input.recipientName || !input.recipientPhone) {
          throw new AppError(400, 'MISSING_SHIPPING_INFO', 'Đơn hàng có dụng cụ vẽ bắt buộc phải cung cấp địa chỉ nhận hàng (SHOP-06)');
        }
      }

      // Thời hạn thanh toán: mặc định 15 phút từ cấu hình (PAY-08, #20)
      const timeoutMinutes = config.orderPaymentTimeoutMinutes || 15;
      const expiredAt = new Date(Date.now() + timeoutMinutes * 60 * 1000);

      const createdOrders = [];

      // Tạo mỗi Teacher một đơn hàng độc lập (BR-04, SHOP-05, #25)
      for (const [teacherId, items] of teacherGroups.entries()) {
        let subtotal = Money.zero();
        let hasSupplyInOrder = false;

        for (const item of items) {
          const itemTotal = item.unitPrice.mul(item.quantity).roundVND();
          subtotal = subtotal.add(itemTotal);
          if (item.itemType === 'SUPPLY') {
            hasSupplyInOrder = true;
          }
        }

        // Phí ship cố định cho đơn có dụng cụ (#8). Phí ship 0% hoa hồng, chuyển đủ cho Teacher.
        const shippingFee = hasSupplyInOrder ? new Money(config.defaultShippingFee || 30000) : Money.zero();
        const totalAmount = subtotal.add(shippingFee).roundVND();

        // 1. Chèn đơn hàng (orders)
        const orderInsertRes = await client.query(
          `INSERT INTO public.orders (
            parent_id, teacher_id, total_amount, shipping_fee, status, expired_at, created_at
          ) VALUES ($1, $2, $3, $4, 'PENDING_PAYMENT', $5, CURRENT_TIMESTAMP)
          RETURNING id, status, total_amount, expired_at`,
          [
            parentId,
            teacherId,
            totalAmount.toString(),
            shippingFee.toString(),
            expiredAt,
          ]
        );
        const orderId = orderInsertRes.rows[0].id;

        // 2. Chèn các dòng chi tiết (order_details)
        for (const item of items) {
          const lineSubtotal = item.unitPrice.mul(item.quantity).roundVND();
          const rate = item.itemType === 'COURSE' ? config.commissionRateCourse : config.commissionRateSupply;
          const { commission, net } = lineSubtotal.calculateCommission(rate);

          await client.query(
            `INSERT INTO public.order_details (
              order_id, product_type, course_id, supply_id, child_id,
              product_name, unit_price, quantity, sub_total, commission_rate,
              commission_amount, net_amount, delivery_status, created_at
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, CURRENT_TIMESTAMP)`,
            [
              orderId,
              item.itemType,
              item.itemType === 'COURSE' ? item.itemId : null,
              item.itemType === 'SUPPLY' ? item.itemId : null,
              item.itemType === 'COURSE' ? item.childId : null,
              item.title,
              item.unitPrice.toString(),
              item.quantity,
              lineSubtotal.toString(),
              rate,
              commission.toString(),
              net.toString(),
              item.itemType === 'SUPPLY' ? 'PENDING' : null,
            ]
          );
        }

        // 3. Tạo bản ghi thanh toán (Payment) ở trạng thái PENDING gắn trực tiếp với đơn (#25, PAY-05)
        const paymentRes = await client.query(
          `INSERT INTO public.payments (
            order_id, teacher_id, gateway, amount, status, created_at
          ) VALUES ($1, $2, 'MOCK', $3, 'PENDING', CURRENT_TIMESTAMP)
          RETURNING id, status, amount`,
          [orderId, teacherId, totalAmount.toString()]
        );

        createdOrders.push({
          orderId,
          teacherId,
          totalAmount: totalAmount.toString(),
          subtotal: subtotal.toString(),
          shippingFee: shippingFee.toString(),
          status: 'PENDING_PAYMENT',
          expiredAt,
          paymentId: paymentRes.rows[0].id,
        });
      }

      // Xóa giỏ hàng nếu đặt từ giỏ
      if (input.useCart && cartId) {
        await CartService.clearCart(cartId, client);
      }

      await client.query('COMMIT');
      return {
        success: true,
        message: 'Tạo đơn hàng thành công (tách theo từng giáo viên)',
        orders: createdOrders,
      };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  /**
   * Xem danh sách lịch sử đơn hàng của Parent (SHOP-08)
   */
  static async getParentOrders(parentId: string) {
    const res = await query(
      `SELECT o.*, tp.full_name AS teacher_name, p.status AS payment_status
       FROM public.orders o
       LEFT JOIN public.teacher_profiles tp ON o.teacher_id = tp.user_id
       LEFT JOIN public.payments p ON o.id = p.order_id
       WHERE o.parent_id = $1
       ORDER BY o.created_at DESC`,
      [parentId]
    );
    return res.rows;
  }

  /**
   * Xem chi tiết 1 đơn hàng (SHOP-08, FUL-01)
   */
  static async getOrderDetail(orderId: string, userId: string, userRole: string) {
    const orderRes = await query(
      `SELECT o.*, tp.full_name AS teacher_name, pp.full_name AS parent_name
       FROM public.orders o
       LEFT JOIN public.teacher_profiles tp ON o.teacher_id = tp.user_id
       LEFT JOIN public.parent_profiles pp ON o.parent_id = pp.user_id
       WHERE o.id = $1`,
      [orderId]
    );

    if (orderRes.rowCount === 0) {
      throw new AppError(404, 'NOT_FOUND', 'Không tìm thấy đơn hàng');
    }
    const order = orderRes.rows[0];

    // Kiểm tra phân quyền
    if (userRole === 'PARENT' && order.parent_id !== userId) {
      throw new AppError(403, 'FORBIDDEN', 'Bạn không có quyền xem đơn hàng này');
    }
    if (userRole === 'TEACHER' && order.teacher_id !== userId) {
      throw new AppError(403, 'FORBIDDEN', 'Đơn hàng này không thuộc về bạn');
    }

    const itemsRes = await query(
      `SELECT od.*, cp.display_name AS child_name
       FROM public.order_details od
       LEFT JOIN public.child_profiles cp ON od.child_id = cp.user_id
       WHERE od.order_id = $1
       ORDER BY od.created_at ASC`,
      [orderId]
    );

    const paymentRes = await query(
      `SELECT * FROM public.payments WHERE order_id = $1`,
      [orderId]
    );

    return {
      order,
      items: itemsRes.rows,
      payment: paymentRes.rows[0] || null,
    };
  }

  /**
   * Teacher xem danh sách đơn hàng có dụng cụ của mình (FUL-01)
   */
  static async getTeacherOrders(teacherId: string) {
    const res = await query(
      `SELECT o.*, pp.full_name AS parent_name, p.status AS payment_status
       FROM public.orders o
       LEFT JOIN public.parent_profiles pp ON o.parent_id = pp.user_id
       LEFT JOIN public.payments p ON o.id = p.order_id
       WHERE o.teacher_id = $1
       ORDER BY o.created_at DESC`,
      [teacherId]
    );
    return res.rows;
  }

  /**
   * Khách hủy đơn chưa thanh toán: trả lại tồn kho giữ chỗ (BR-11)
   */
  static async cancelOrder(orderId: string, parentId: string) {
    const client = await getClient();
    try {
      await client.query('BEGIN');

      const oRes = await client.query(
        `SELECT id, status FROM public.orders WHERE id = $1 AND parent_id = $2 FOR UPDATE`,
        [orderId, parentId]
      );
      if (oRes.rowCount === 0) {
        throw new AppError(404, 'NOT_FOUND', 'Không tìm thấy đơn hàng');
      }

      const order = oRes.rows[0];
      if (order.status !== 'PENDING_PAYMENT') {
        throw new AppError(400, 'CANNOT_CANCEL', 'Chỉ có thể hủy đơn hàng đang chờ thanh toán');
      }

      // 1. Trả lại tồn kho giữ chỗ cho các dòng dụng cụ
      const details = await client.query(
        `SELECT supply_id, quantity FROM public.order_details 
         WHERE order_id = $1 AND product_type = 'SUPPLY'`,
        [orderId]
      );
      for (const row of details.rows) {
        await client.query(
          `UPDATE public.supplies 
           SET reserved_quantity = GREATEST(0, reserved_quantity - $1)
           WHERE id = $2`,
          [row.quantity, row.supply_id]
        );
      }

      // 2. Đổi trạng thái đơn sang CANCELLED
      await client.query(
        `UPDATE public.orders SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP WHERE id = $1`,
        [orderId]
      );

      await client.query('COMMIT');
      return { success: true, message: 'Đã hủy đơn hàng và trả lại tồn kho thành công' };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  /**
   * Job định kỳ quét và hết hạn các đơn PENDING_PAYMENT quá hạn (PAY-08)
   * Khóa job bằng PostgreSQL advisory lock (987654321) để chống chạy trùng nhiều instance.
   */
  static async expirePendingOrders(): Promise<{ expiredCount: number }> {
    const client = await getClient();
    try {
      // 1. Khóa bằng PostgreSQL advisory lock
      const lockRes = await client.query('SELECT pg_try_advisory_lock(987654321) AS acquired');
      if (!lockRes.rows[0].acquired) {
        return { expiredCount: 0 };
      }

      await client.query('BEGIN');

      // Tìm các đơn PENDING_PAYMENT quá hạn
      const expiredOrdersRes = await client.query(
        `SELECT id FROM public.orders 
         WHERE status = 'PENDING_PAYMENT' AND expired_at <= CURRENT_TIMESTAMP
         FOR UPDATE`
      );

      let count = 0;
      for (const ord of expiredOrdersRes.rows) {
        const orderId = ord.id;

        // Trả lại tồn kho giữ chỗ cho từng dòng dụng cụ
        const details = await client.query(
          `SELECT supply_id, quantity FROM public.order_details 
           WHERE order_id = $1 AND product_type = 'SUPPLY'`,
          [orderId]
        );
        for (const row of details.rows) {
          await client.query(
            `UPDATE public.supplies 
             SET reserved_quantity = GREATEST(0, reserved_quantity - $1)
             WHERE id = $2`,
            [row.quantity, row.supply_id]
          );
        }

        // Cập nhật trạng thái đơn thành EXPIRED
        await client.query(
          `UPDATE public.orders SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP WHERE id = $1`,
          [orderId]
        );

        count++;
      }

      await client.query('COMMIT');
      return { expiredCount: count };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      await client.query('SELECT pg_advisory_unlock(987654321)').catch(() => {});
      client.release();
    }
  }
}
