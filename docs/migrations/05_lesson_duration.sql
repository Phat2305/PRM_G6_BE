-- =====================================================================
-- Thời lượng video của bài học (lessons.duration_seconds).
-- Dùng để child_progress chỉ coi bài là "đã học xong" khi bé xem ít nhất 90% video.
-- Chạy THỦ CÔNG trong Supabase SQL Editor / DB dev. Idempotent, không xóa dữ liệu.
-- Bảng lessons thuộc BE1: cần BE1 đồng ý trước khi áp lên Supabase.
-- Video đã xử lý trước đây có duration_seconds NULL: child_progress không kiểm với các bài này,
-- cột sẽ được điền khi video được tải lên lại và xử lý xong.
-- =====================================================================
BEGIN;

ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS duration_seconds integer;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_lessons_duration_positive') THEN
        ALTER TABLE public.lessons
            ADD CONSTRAINT chk_lessons_duration_positive
            CHECK (duration_seconds IS NULL OR duration_seconds > 0);
    END IF;
END $$;

COMMIT;

-- Hoàn tác:
--   ALTER TABLE public.lessons DROP CONSTRAINT IF EXISTS chk_lessons_duration_positive;
--   ALTER TABLE public.lessons DROP COLUMN IF EXISTS duration_seconds;
