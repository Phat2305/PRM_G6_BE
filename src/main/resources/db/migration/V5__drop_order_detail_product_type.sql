-- =====================================================================
-- FLYWAY V5 MIGRATION: BỎ order_details.product_type
-- Loại dòng suy ra từ course_id / supply_id / customize_request_id.
-- Không tự chạy trên Supabase; xuất file này và chạy thủ công (sau V3, V4).
-- =====================================================================

ALTER TABLE public.order_details DROP CONSTRAINT IF EXISTS chk_order_detail_type_exclusive;
ALTER TABLE public.order_details DROP COLUMN IF EXISTS product_type;

-- Đúng một trong ba cột khóa ngoại có giá trị. Dụng cụ phải có delivery_status.
-- Khóa học có thể chưa gán bé (child_id NULL), gán sau khi thanh toán.
ALTER TABLE public.order_details ADD CONSTRAINT chk_order_detail_type_exclusive CHECK (
    (course_id IS NOT NULL AND supply_id IS NULL AND customize_request_id IS NULL) OR
    (course_id IS NULL AND supply_id IS NOT NULL AND customize_request_id IS NULL AND delivery_status IS NOT NULL) OR
    (course_id IS NULL AND supply_id IS NULL AND customize_request_id IS NOT NULL)
);
