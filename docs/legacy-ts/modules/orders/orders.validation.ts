import { z } from 'zod';

export const CheckoutItemSchema = z.object({
  itemType: z.enum(['COURSE', 'SUPPLY']),
  courseId: z.string().uuid().optional(),
  supplyId: z.string().uuid().optional(),
  childId: z.string().uuid().optional(),
  quantity: z.number().int().min(1).default(1),
});

export const CheckoutSchema = z.object({
  useCart: z.boolean().default(true),
  items: z.array(CheckoutItemSchema).optional(),
  recipientName: z.string().min(1).optional(),
  recipientPhone: z.string().min(1).optional(),
  shippingAddress: z.string().min(1).optional(),
});
