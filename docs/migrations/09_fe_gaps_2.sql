-- =====================================================================
-- 09: Cột còn thiếu đợt 2 (xem PRM393_G6_FE/docs/ke-hoach-noi-api.md).
-- Chạy THỦ CÔNG trong Supabase SQL Editor / DB dev TRƯỚC khi chạy BE mới:
-- mọi truy vấn child_profiles / lessons sẽ lỗi nếu thiếu cột.
-- Idempotent, không xóa dữ liệu.
-- =====================================================================
BEGIN;

-- Cờ "cho giáo viên xem tên con" (ACC-10 / ASG-09). Mặc định false: giáo viên thấy bé ẩn danh tới khi phụ huynh bật.
ALTER TABLE public.child_profiles ADD COLUMN IF NOT EXISTS share_info boolean NOT NULL DEFAULT false;

-- Tiêu đề bài tập của bài học (cột assignment_prompt đã thêm ở 08 giữ phần hướng dẫn).
ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS assignment_title character varying(255);

COMMIT;

-- Hoàn tác:
--   ALTER TABLE public.lessons DROP COLUMN IF EXISTS assignment_title;
--   ALTER TABLE public.child_profiles DROP COLUMN IF EXISTS share_info;
