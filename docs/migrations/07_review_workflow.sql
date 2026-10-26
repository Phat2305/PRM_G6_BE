-- =====================================================================
-- Quy trình Staff duyệt khóa học và bài đăng (yêu cầu customize).
--   rejection_count: số lần Staff đã từ chối trong lượt duyệt hiện tại (tối đa 3, được đặt lại về 0 khi được duyệt).
--   review_note:     lý do từ chối gần nhất của Staff, để chủ bài/giáo viên sửa lại.
-- Chạy THỦ CÔNG trong Supabase SQL Editor / DB dev TRƯỚC khi chạy BE bản mới (entity map hai cột này,
-- thiếu cột thì mọi truy vấn khóa học/yêu cầu đều lỗi). Idempotent, không xóa dữ liệu.
-- Bảng courses, customize_requests thuộc BE1: cần BE1 đồng ý trước khi áp lên Supabase.
-- Hàng cũ: rejection_count = 0, review_note NULL. Yêu cầu đang OPEN coi như đã được duyệt.
-- =====================================================================
BEGIN;

ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS rejection_count integer NOT NULL DEFAULT 0;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS review_note text;

ALTER TABLE public.customize_requests ADD COLUMN IF NOT EXISTS rejection_count integer NOT NULL DEFAULT 0;
ALTER TABLE public.customize_requests ADD COLUMN IF NOT EXISTS review_note text;

COMMIT;

-- Hoàn tác:
--   ALTER TABLE public.courses DROP COLUMN IF EXISTS review_note;
--   ALTER TABLE public.courses DROP COLUMN IF EXISTS rejection_count;
--   ALTER TABLE public.customize_requests DROP COLUMN IF EXISTS review_note;
--   ALTER TABLE public.customize_requests DROP COLUMN IF EXISTS rejection_count;
