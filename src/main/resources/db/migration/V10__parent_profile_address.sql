-- SHOP-06: chuyển từ sổ địa chỉ sang một địa chỉ trong parent_profiles.
-- Cập nhật BE và FE trước khi chạy thủ công trong Supabase SQL Editor.
-- Xuất bản sao parent_addresses trước: các địa chỉ phụ và tên/SĐT người nhận riêng
-- sẽ không còn trong ứng dụng. Không ghi đè tên, SĐT hoặc shipping_address đã có.
-- Một câu DO chạy nguyên tử, dùng được cả với Flyway và SQL Editor.
DO $$
BEGIN
    IF to_regclass('public.parent_addresses') IS NULL THEN
        RETURN;
    END IF;

    LOCK TABLE public.parent_addresses, public.parent_profiles IN ACCESS EXCLUSIVE MODE;

    IF EXISTS (
        SELECT 1 FROM public.parent_addresses a
        LEFT JOIN public.parent_profiles p ON p.user_id = a.parent_id
        WHERE p.user_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Có địa chỉ chưa có parent_profiles; bổ sung hồ sơ trước khi xóa bảng';
    END IF;

    IF EXISTS (
        SELECT parent_id FROM public.parent_addresses
        GROUP BY parent_id
        HAVING count(*) FILTER (WHERE is_default) <> 1
    ) OR EXISTS (
        SELECT 1 FROM public.parent_addresses
        WHERE is_default AND NULLIF(btrim(address_line), '') IS NULL
    ) THEN
        RAISE EXCEPTION 'Mỗi phụ huynh cần đúng một địa chỉ mặc định không trống';
    END IF;

    UPDATE public.parent_profiles p
    SET shipping_address = btrim(a.address_line),
        phone = CASE WHEN NULLIF(btrim(p.phone), '') IS NULL THEN a.phone ELSE p.phone END
    FROM public.parent_addresses a
    WHERE a.parent_id = p.user_id AND a.is_default
      AND NULLIF(btrim(p.shipping_address), '') IS NULL;

    -- Tự kiểm tra trước DROP; lỗi ở bất kỳ bước nào sẽ hoàn tác toàn bộ câu DO.
    IF EXISTS (
        SELECT 1 FROM public.parent_addresses a
        JOIN public.parent_profiles p ON p.user_id = a.parent_id
        WHERE a.is_default AND NULLIF(btrim(p.shipping_address), '') IS NULL
    ) THEN
        RAISE EXCEPTION 'Chưa chuyển đủ địa chỉ mặc định; giữ nguyên bảng cũ';
    END IF;

    DROP TABLE public.parent_addresses;
END $$;
