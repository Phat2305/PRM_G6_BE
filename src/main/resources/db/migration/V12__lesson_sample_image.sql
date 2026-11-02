-- Ảnh tranh mẫu của bài tập vẽ: đường dẫn công khai /api/images/lessons/{uuid}, ảnh lưu ở R2.
ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS sample_image_url character varying(255);
