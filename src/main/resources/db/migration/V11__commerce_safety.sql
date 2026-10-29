-- SHOP-03, PAY-01, FIN-09: chạy thủ công trên Supabase trước khi triển khai BE mới.
-- Không sửa/xóa dữ liệu sổ cái cũ; các số dư sai từ trước cần được đối soát riêng.
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS recipient_name varchar(255);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS recipient_phone varchar(20);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS shipping_address text;

ALTER TABLE public.money_ledger
    ADD COLUMN IF NOT EXISTS affects_available boolean NOT NULL DEFAULT true;

-- Tăng từ mã cũ lớn nhất/thời điểm hiện tại để không đụng các link đã từng phát hành.
CREATE SEQUENCE IF NOT EXISTS public.payos_order_code_seq
    AS bigint MINVALUE 1 MAXVALUE 9007199254740991;
SELECT setval('public.payos_order_code_seq', GREATEST(
    (SELECT last_value FROM public.payos_order_code_seq),
    COALESCE((SELECT MAX(gateway_order_code) FROM public.payments), 0),
    FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::bigint
) + 1, false);
