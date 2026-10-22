-- =====================================================================
-- FLYWAY V4 MIGRATION: KHÓA HỌC CÓ THỂ CHƯA GÁN BÉ KHI ĐẶT HÀNG (SHOP-04)
-- Bé được chọn sau thanh toán qua POST /api/v1/orders/{id}/details/{detailId}/assign-child.
-- Không tự chạy trên Supabase; xuất file này và chạy thủ công.
-- =====================================================================

ALTER TABLE public.order_details DROP CONSTRAINT IF EXISTS chk_order_detail_type_exclusive;
ALTER TABLE public.order_details ADD CONSTRAINT chk_order_detail_type_exclusive CHECK (
    (product_type = 'COURSE' AND course_id IS NOT NULL AND supply_id IS NULL AND customize_request_id IS NULL) OR
    (product_type = 'SUPPLY' AND course_id IS NULL AND supply_id IS NOT NULL AND customize_request_id IS NULL AND delivery_status IS NOT NULL) OR
    (product_type = 'CUSTOMIZE' AND course_id IS NULL AND supply_id IS NULL AND customize_request_id IS NOT NULL)
);
