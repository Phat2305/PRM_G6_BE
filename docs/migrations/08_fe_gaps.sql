-- =====================================================================
-- 08: Các cột/bảng FE còn thiếu (xem PRM393_G6_FE/docs/ke-hoach-noi-api.md).
-- Chạy THỦ CÔNG trong Supabase SQL Editor / DB dev TRƯỚC khi chạy BE mới.
-- Idempotent, không xóa dữ liệu. Mỗi khối ghi mã task dùng nó.
-- =====================================================================
BEGIN;

-- B1: ngày nộp / ngày chấm bài (FE đang hiện bài nộp không có ngày).
ALTER TABLE public.submissions ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.submissions ADD COLUMN IF NOT EXISTS graded_at timestamptz;

-- B3: đề bài tập của bài học (hiện chỉ có cờ has_assignment).
ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS assignment_prompt text;

-- B4: số điện thoại phụ huynh (avatar dùng chung R2 avatars/{userId}, không cần cột).
ALTER TABLE public.parent_profiles ADD COLUMN IF NOT EXISTS phone character varying(20);

-- B5: sổ địa chỉ giao hàng của phụ huynh.
CREATE TABLE IF NOT EXISTS public.parent_addresses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    recipient_name character varying(255) NOT NULL,
    phone character varying(20) NOT NULL,
    address_line text NOT NULL,
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_parent_addresses_parent ON public.parent_addresses(parent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_parent_addresses_default
    ON public.parent_addresses(parent_id) WHERE is_default;

-- B6: phí ship riêng của giáo viên (NULL = dùng system_config shipping_fee_default).
ALTER TABLE public.teacher_profiles ADD COLUMN IF NOT EXISTS shipping_fee numeric(12,0);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_teacher_shipping_fee') THEN
        ALTER TABLE public.teacher_profiles
            ADD CONSTRAINT chk_teacher_shipping_fee CHECK (shipping_fee IS NULL OR shipping_fee >= 0);
    END IF;
END $$;

-- B7: khóa R2 của file chứng chỉ, mỗi dòng một khóa, cùng thứ tự với certificates_text.
ALTER TABLE public.teacher_profiles ADD COLUMN IF NOT EXISTS certificate_keys text;

-- B9: giỏ cho phép khóa học chưa chọn bé (checkout vẫn bắt buộc có bé).
ALTER TABLE public.cart_items DROP CONSTRAINT IF EXISTS chk_cart_item_product_type;
ALTER TABLE public.cart_items ADD CONSTRAINT chk_cart_item_product_type CHECK (
    (product_type = 'COURSE' AND course_id IS NOT NULL AND supply_id IS NULL AND quantity = 1) OR
    (product_type = 'SUPPLY' AND course_id IS NULL AND supply_id IS NOT NULL AND child_id IS NULL)
);
-- NULL không trùng nhau trong unique index nên chặn riêng dòng khóa học chưa chọn bé.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_course_nochild
    ON public.cart_items (cart_id, course_id) WHERE product_type = 'COURSE' AND child_id IS NULL;

COMMIT;

-- Hoàn tác:
--   DROP INDEX IF EXISTS uq_cart_course_nochild;
--   (chỉ khi không còn dòng COURSE child_id NULL) ALTER TABLE cart_items DROP CONSTRAINT chk_cart_item_product_type;
--     rồi thêm lại bản cũ có "AND child_id IS NOT NULL" ở nhánh COURSE.
--   ALTER TABLE teacher_profiles DROP COLUMN IF EXISTS certificate_keys, DROP COLUMN IF EXISTS shipping_fee;
--   DROP TABLE IF EXISTS parent_addresses;
--   ALTER TABLE parent_profiles DROP COLUMN IF EXISTS phone;
--   ALTER TABLE lessons DROP COLUMN IF EXISTS assignment_prompt;
--   ALTER TABLE submissions DROP COLUMN IF EXISTS graded_at, DROP COLUMN IF EXISTS created_at;
