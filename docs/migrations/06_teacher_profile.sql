-- =====================================================================
-- Hồ sơ giáo viên: số điện thoại và giới thiệu bản thân (teacher_profiles.phone, .bio).
-- Chứng chỉ dùng cột có sẵn certificates_text (mỗi dòng một chứng chỉ, tối đa 10).
-- Chạy THỦ CÔNG trong Supabase SQL Editor / DB dev TRƯỚC khi chạy BE mới:
-- GET/PUT /api/v1/users/me của giáo viên đọc hai cột này nên sẽ lỗi nếu thiếu.
-- Idempotent, không xóa dữ liệu.
-- =====================================================================
BEGIN;

ALTER TABLE public.teacher_profiles ADD COLUMN IF NOT EXISTS phone character varying(20);
ALTER TABLE public.teacher_profiles ADD COLUMN IF NOT EXISTS bio text;

COMMIT;

-- Hoàn tác:
--   ALTER TABLE public.teacher_profiles DROP COLUMN IF EXISTS bio;
--   ALTER TABLE public.teacher_profiles DROP COLUMN IF EXISTS phone;
