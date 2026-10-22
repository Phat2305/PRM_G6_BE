-- =====================================================================
-- QUICKFIX Supabase: chỉ phần webhook PayOS + job nền.
-- Chạy THỦ CÔNG trong Supabase SQL Editor. Idempotent, không xóa dữ liệu.
-- Các bảng khác (order_details...) xử lý riêng bằng 03_supabase_sync_missing.sql.
-- =====================================================================
BEGIN;

-- payments: bổ sung cột còn thiếu (entity Payment.java)
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS teacher_id UUID REFERENCES public.users(id);
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS payment_url TEXT;
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(15, 0) NOT NULL DEFAULT 0;
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS net_amount NUMERIC(15, 0) NOT NULL DEFAULT 0;
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS gateway_fee NUMERIC(15, 0) DEFAULT 0;
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS gateway_order_code BIGINT;
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS released_at TIMESTAMPTZ;
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Điền teacher_id cho payment cũ từ đơn hàng tương ứng (chỉ khi orders có cột teacher_id)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'orders' AND column_name = 'teacher_id') THEN
        UPDATE public.payments p SET teacher_id = o.teacher_id
        FROM public.orders o
        WHERE p.order_id = o.id AND p.teacher_id IS NULL AND o.teacher_id IS NOT NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_gateway_order_code
    ON public.payments (gateway_order_code) WHERE gateway_order_code IS NOT NULL;

-- Cổng PayOS: bỏ ràng buộc cũ (VNPAY/MOMO/MOCK), chỉ nhận PAYOS và MOCK cho dòng mới
ALTER TABLE public.payments DROP CONSTRAINT IF EXISTS payments_gateway_check;
ALTER TABLE public.payments ADD CONSTRAINT payments_gateway_check
    CHECK (gateway IN ('PAYOS', 'MOCK')) NOT VALID;

-- webhook_logs (entity WebhookLog.java)
CREATE TABLE IF NOT EXISTS public.webhook_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway VARCHAR(20) NOT NULL,
    event_type VARCHAR(50),
    payload JSONB NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    processed_status VARCHAR(20) NOT NULL
        CHECK (processed_status IN ('SUCCESS', 'FAILED', 'IGNORED', 'WARNING')),
    response_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Entity WebhookLog map payload là JSON: nếu bảng đã có sẵn kiểu TEXT thì đổi sang JSONB
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'webhook_logs'
                 AND column_name = 'payload' AND data_type <> 'jsonb') THEN
        ALTER TABLE public.webhook_logs ALTER COLUMN payload TYPE JSONB USING payload::jsonb;
    END IF;
END $$;
ALTER TABLE public.webhook_logs ADD COLUMN IF NOT EXISTS event_type VARCHAR(50);
ALTER TABLE public.webhook_logs ADD COLUMN IF NOT EXISTS response_message TEXT;

-- Job nền
CREATE TABLE IF NOT EXISTS public.scheduled_job_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    records_affected INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS public.shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

COMMIT;
