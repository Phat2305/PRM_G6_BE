import { query } from '../../config/db';
import { AppError } from '../../middlewares/error.middleware';

export class ProductsService {
  /**
   * Duyệt danh sách dụng cụ vẽ (SHOP-01)
   */
  static async listSupplies(filters: { search?: string }) {
    let sql = `
      SELECT s.id, s.name, s.description, s.image_url, s.price,
             s.stock_quantity, s.reserved_quantity,
             (s.stock_quantity - s.reserved_quantity) AS available_stock,
             s.teacher_id, tp.full_name AS teacher_name
      FROM public.supplies s
      LEFT JOIN public.teacher_profiles tp ON s.teacher_id = tp.user_id
      WHERE (s.status = 'ACTIVE' OR s.status = 'active')
    `;
    const params: any[] = [];

    if (filters.search) {
      params.push(`%${filters.search}%`);
      sql += ` AND s.name ILIKE $${params.length}`;
    }

    sql += ' ORDER BY s.id DESC';
    const res = await query(sql, params);
    return res.rows;
  }

  /**
   * Chi tiết dụng cụ vẽ
   */
  static async getSupplyDetail(supplyId: string) {
    const res = await query(
      `SELECT s.id, s.name, s.description, s.image_url, s.price,
              s.stock_quantity, s.reserved_quantity,
              (s.stock_quantity - s.reserved_quantity) AS available_stock,
              s.teacher_id, tp.full_name AS teacher_name
       FROM public.supplies s
       LEFT JOIN public.teacher_profiles tp ON s.teacher_id = tp.user_id
       WHERE s.id = $1`,
      [supplyId]
    );

    if (res.rowCount === 0) {
      throw new AppError(404, 'NOT_FOUND', 'Không tìm thấy dụng cụ vẽ');
    }

    return res.rows[0];
  }

  /**
   * Học cụ gợi ý hiển thị theo bài học dùng đến (SHOP-01, CRS-08, #15)
   */
  static async getRecommendedSuppliesByLesson(lessonId: string) {
    const res = await query(
      `SELECT s.id, s.name, s.price, s.image_url, 
              (s.stock_quantity - s.reserved_quantity) AS available_stock,
              ls.quantity AS required_quantity
       FROM public.lesson_supplies ls
       JOIN public.supplies s ON ls.supply_id = s.id
       WHERE ls.lesson_id = $1`,
      [lessonId]
    );

    return res.rows;
  }
}
