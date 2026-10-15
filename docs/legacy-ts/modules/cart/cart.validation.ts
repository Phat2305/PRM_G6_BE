import { z } from 'zod';

export const AddToCartSchema = z.object({
  itemType: z.enum(['COURSE', 'SUPPLY']),
  courseId: z.string().uuid().optional(),
  supplyId: z.string().uuid().optional(),
  childId: z.string().uuid().optional(),
  quantity: z.number().int().min(1).default(1),
}).refine((data) => {
  if (data.itemType === 'COURSE') {
    return !!data.courseId && !!data.childId && data.quantity === 1;
  }
  if (data.itemType === 'SUPPLY') {
    return !!data.supplyId && !data.childId;
  }
  return false;
}, {
  message: 'Thông tin sản phẩm thêm vào giỏ không hợp lệ (Khóa học cần courseId + childId và số lượng = 1; Dụng cụ cần supplyId)',
});

export const UpdateCartItemSchema = z.object({
  quantity: z.number().int().min(1),
});
