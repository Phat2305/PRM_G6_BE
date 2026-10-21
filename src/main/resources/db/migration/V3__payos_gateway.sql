-- =====================================================================
-- FLYWAY V3 MIGRATION: CỔNG PAYOS (PAY-01)
-- Không tự chạy trên Supabase; xuất file này và chạy thủ công.
-- =====================================================================

-- PayOS dùng orderCode số nguyên để khớp webhook với payment
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS gateway_order_code BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_gateway_order_code
    ON public.payments (gateway_order_code) WHERE gateway_order_code IS NOT NULL;

-- Chỉ PayOS cho giao dịch mới. MOCK giữ lại cho test tích hợp.
-- NOT VALID: các dòng VNPAY/MOMO cũ không bị kiểm lại, dòng mới vẫn bị chặn.
ALTER TABLE public.payments DROP CONSTRAINT IF EXISTS payments_gateway_check;
ALTER TABLE public.payments ADD CONSTRAINT payments_gateway_check
    CHECK (gateway IN ('PAYOS', 'MOCK')) NOT VALID;
