-- =====================================================================
-- FLYWAY V1 INITIAL MIGRATION: BE2 SCHEMA INIT
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Supplies columns
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'supplies' AND column_name = 'reserved_quantity'
    ) THEN
        ALTER TABLE public.supplies 
        ADD COLUMN reserved_quantity INTEGER NOT NULL DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'supplies' AND column_name = 'sold_quantity'
    ) THEN
        ALTER TABLE public.supplies 
        ADD COLUMN sold_quantity INTEGER NOT NULL DEFAULT 0;
    END IF;
END $$;

ALTER TABLE public.supplies DROP CONSTRAINT IF EXISTS chk_supplies_stock_non_negative;
ALTER TABLE public.supplies ADD CONSTRAINT chk_supplies_stock_non_negative 
    CHECK (stock_quantity >= 0 AND reserved_quantity >= 0 AND sold_quantity >= 0);

ALTER TABLE public.supplies DROP CONSTRAINT IF EXISTS chk_supplies_available_stock;
ALTER TABLE public.supplies ADD CONSTRAINT chk_supplies_available_stock 
    CHECK (stock_quantity >= reserved_quantity);

-- Course Reviews created_at
ALTER TABLE public.course_reviews ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE public.course_reviews ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Carts
CREATE TABLE IF NOT EXISTS public.carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID NOT NULL UNIQUE REFERENCES public.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES public.carts(id) ON DELETE CASCADE,
    product_type VARCHAR(20) NOT NULL CHECK (product_type IN ('COURSE', 'SUPPLY')),
    course_id UUID REFERENCES public.courses(id) ON DELETE CASCADE,
    supply_id UUID REFERENCES public.supplies(id) ON DELETE CASCADE,
    child_id UUID REFERENCES public.child_profiles(user_id) ON DELETE SET NULL,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cart_item_product_type CHECK (
        (product_type = 'COURSE' AND course_id IS NOT NULL AND supply_id IS NULL AND child_id IS NOT NULL AND quantity = 1) OR
        (product_type = 'SUPPLY' AND course_id IS NULL AND supply_id IS NOT NULL AND child_id IS NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_course_child 
    ON public.cart_items (cart_id, course_id, child_id) 
    WHERE product_type = 'COURSE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_supply 
    ON public.cart_items (cart_id, supply_id) 
    WHERE product_type = 'SUPPLY';

-- Orders (bảng đã có trong schema nền ART_COUSE.sql -> bổ sung cột & ràng buộc bằng ALTER TABLE)
CREATE TABLE IF NOT EXISTS public.orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID NOT NULL REFERENCES public.users(id),
    teacher_id UUID NOT NULL REFERENCES public.users(id),
    total_amount NUMERIC(15, 0) NOT NULL CHECK (total_amount >= 0),
    shipping_fee NUMERIC(15, 0) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT' 
        CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'EXPIRED', 'CANCELLED')),
    expired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.orders ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE public.orders ALTER COLUMN shipping_fee SET DEFAULT 0;
ALTER TABLE public.orders ALTER COLUMN status SET DEFAULT 'PENDING_PAYMENT';
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS chk_orders_total_amount;
ALTER TABLE public.orders ADD CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0);

ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS chk_orders_shipping_fee;
ALTER TABLE public.orders ADD CONSTRAINT chk_orders_shipping_fee CHECK (shipping_fee >= 0);

ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS chk_orders_status;
ALTER TABLE public.orders ADD CONSTRAINT chk_orders_status 
    CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'EXPIRED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_orders_parent_id ON public.orders(parent_id);
CREATE INDEX IF NOT EXISTS idx_orders_teacher_id ON public.orders(teacher_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON public.orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON public.orders(created_at);

-- Order Details (giữ nguyên bảng cũ orders_detail của nền, tạo bảng chuẩn order_details cho BE2)
CREATE TABLE IF NOT EXISTS public.order_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
    product_type VARCHAR(20) NOT NULL CHECK (product_type IN ('COURSE', 'SUPPLY', 'CUSTOMIZE')),
    course_id UUID REFERENCES public.courses(id),
    supply_id UUID REFERENCES public.supplies(id),
    customize_request_id UUID REFERENCES public.customize_requests(id),
    child_id UUID REFERENCES public.child_profiles(user_id),
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(15, 0) NOT NULL CHECK (unit_price >= 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    sub_total NUMERIC(15, 0) NOT NULL CHECK (sub_total >= 0),
    commission_rate NUMERIC(5, 4) NOT NULL DEFAULT 0.0 CHECK (commission_rate >= 0 AND commission_rate <= 1),
    commission_amount NUMERIC(15, 0) NOT NULL DEFAULT 0 CHECK (commission_amount >= 0),
    net_amount NUMERIC(15, 0) NOT NULL DEFAULT 0 CHECK (net_amount >= 0),
    delivery_status VARCHAR(20) CHECK (
        delivery_status IS NULL OR 
        delivery_status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'REJECTED', 'DELIVERY_FAILED', 'REFUNDED')
    ),
    shipping_provider VARCHAR(100),
    tracking_code VARCHAR(100),
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    confirmed_delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_detail_type_exclusive CHECK (
        (product_type = 'COURSE' AND course_id IS NOT NULL AND supply_id IS NULL AND customize_request_id IS NULL AND child_id IS NOT NULL) OR
        (product_type = 'SUPPLY' AND course_id IS NULL AND supply_id IS NOT NULL AND customize_request_id IS NULL AND delivery_status IS NOT NULL) OR
        (product_type = 'CUSTOMIZE' AND course_id IS NULL AND supply_id IS NULL AND customize_request_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_order_details_order_id ON public.order_details(order_id);
CREATE INDEX IF NOT EXISTS idx_order_details_course_id ON public.order_details(course_id);
CREATE INDEX IF NOT EXISTS idx_order_details_supply_id ON public.order_details(supply_id);
CREATE INDEX IF NOT EXISTS idx_order_details_child_id ON public.order_details(child_id);

-- Payments (giữ nguyên bảng cũ "Payment" của nền, tạo bảng chuẩn payments cho BE2)
CREATE TABLE IF NOT EXISTS public.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES public.orders(id),
    teacher_id UUID NOT NULL REFERENCES public.users(id),
    gateway VARCHAR(20) NOT NULL CHECK (gateway IN ('VNPAY', 'MOMO', 'MOCK')),
    gateway_transaction_id VARCHAR(100),
    payment_url TEXT,
    amount NUMERIC(15, 0) NOT NULL CHECK (amount >= 0),
    commission_amount NUMERIC(15, 0) NOT NULL DEFAULT 0 CHECK (commission_amount >= 0),
    net_amount NUMERIC(15, 0) NOT NULL DEFAULT 0 CHECK (net_amount >= 0),
    gateway_fee NUMERIC(15, 0) DEFAULT 0 CHECK (gateway_fee >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'HELD', 'RELEASED', 'FAILED', 'REFUNDED')),
    paid_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_gateway_tx 
    ON public.payments (gateway, gateway_transaction_id) 
    WHERE gateway_transaction_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON public.payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_teacher_id ON public.payments(teacher_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON public.payments(status);

-- Webhook logs
CREATE TABLE IF NOT EXISTS public.webhook_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway VARCHAR(20) NOT NULL,
    event_type VARCHAR(50),
    payload TEXT NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    processed_status VARCHAR(20) NOT NULL 
        CHECK (processed_status IN ('SUCCESS', 'FAILED', 'IGNORED', 'WARNING')),
    response_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_logs_gateway ON public.webhook_logs(gateway);
CREATE INDEX IF NOT EXISTS idx_webhook_logs_created_at ON public.webhook_logs(created_at);

-- Refunds (bảng đã có trong schema nền ART_COUSE.sql -> bổ sung cột & cập nhật FK bằng ALTER TABLE)
CREATE TABLE IF NOT EXISTS public.refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES public.payments(id),
    order_id UUID NOT NULL REFERENCES public.orders(id),
    parent_id UUID NOT NULL REFERENCES public.users(id),
    total_amount NUMERIC(15, 0) NOT NULL CHECK (total_amount > 0),
    bank_info TEXT NOT NULL,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED', 'FAILED')),
    refund_transaction_code VARCHAR(100),
    proof_image_url TEXT,
    handled_by UUID REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.refunds ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE public.refunds ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE public.refunds ADD COLUMN IF NOT EXISTS order_id UUID REFERENCES public.orders(id);
ALTER TABLE public.refunds ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE public.refunds ADD COLUMN IF NOT EXISTS refund_transaction_code VARCHAR(100);
ALTER TABLE public.refunds ADD COLUMN IF NOT EXISTS proof_image_url TEXT;
ALTER TABLE public.refunds ADD COLUMN IF NOT EXISTS handled_by UUID REFERENCES public.users(id);
ALTER TABLE public.refunds ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.refunds ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.refunds ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE public.refunds DROP CONSTRAINT IF EXISTS fk_refunds_payment;
ALTER TABLE public.refunds ADD CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES public.payments(id);

ALTER TABLE public.refunds DROP CONSTRAINT IF EXISTS chk_refunds_total_amount;
ALTER TABLE public.refunds ADD CONSTRAINT chk_refunds_total_amount CHECK (total_amount > 0);

ALTER TABLE public.refunds DROP CONSTRAINT IF EXISTS chk_refunds_status;
ALTER TABLE public.refunds ADD CONSTRAINT chk_refunds_status 
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED', 'FAILED'));

CREATE TABLE IF NOT EXISTS public.refund_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_id UUID NOT NULL REFERENCES public.refunds(id) ON DELETE CASCADE,
    order_detail_id UUID NOT NULL REFERENCES public.order_details(id),
    amount NUMERIC(15, 0) NOT NULL CHECK (amount > 0),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refunds_payment_id ON public.refunds(payment_id);
CREATE INDEX IF NOT EXISTS idx_refunds_order_id ON public.refunds(order_id);
CREATE INDEX IF NOT EXISTS idx_refund_items_refund_id ON public.refund_items(refund_id);
CREATE INDEX IF NOT EXISTS idx_refund_items_detail_id ON public.refund_items(order_detail_id);

-- Withdrawal Requests (bảng đã có trong schema nền ART_COUSE.sql -> ALTER TABLE & đổi kiểu status BOOLEAN sang VARCHAR(20) bằng USING)
CREATE TABLE IF NOT EXISTS public.withdrawal_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.users(id),
    total_amount NUMERIC(15, 0) NOT NULL CHECK (total_amount > 0),
    bank_info TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'APPROVED', 'PAID', 'REJECTED')),
    approved_by UUID REFERENCES public.users(id),
    paid_by UUID REFERENCES public.users(id),
    rejected_reason TEXT,
    transaction_code VARCHAR(100),
    proof_image_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.withdrawal_requests ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE public.withdrawal_requests ALTER COLUMN approved_by DROP NOT NULL;
ALTER TABLE public.withdrawal_requests ADD COLUMN IF NOT EXISTS bank_info TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawal_requests ADD COLUMN IF NOT EXISTS paid_by UUID REFERENCES public.users(id);
ALTER TABLE public.withdrawal_requests ADD COLUMN IF NOT EXISTS rejected_reason TEXT;
ALTER TABLE public.withdrawal_requests ADD COLUMN IF NOT EXISTS transaction_code VARCHAR(100);
ALTER TABLE public.withdrawal_requests ADD COLUMN IF NOT EXISTS proof_image_url TEXT;
ALTER TABLE public.withdrawal_requests ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.withdrawal_requests ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

DROP INDEX IF EXISTS public.uq_teacher_active_withdrawal;
DROP INDEX IF EXISTS public.idx_withdrawal_requests_status;
ALTER TABLE public.withdrawal_requests DROP CONSTRAINT IF EXISTS chk_withdrawal_requests_status;
ALTER TABLE public.withdrawal_requests ALTER COLUMN status DROP DEFAULT;
ALTER TABLE public.withdrawal_requests 
    ALTER COLUMN status TYPE VARCHAR(20) 
    USING (
        CASE 
            WHEN status::text IN ('true', 't', '1') THEN 'PAID'
            WHEN status::text IN ('false', 'f', '0') THEN 'PENDING'
            ELSE status::text
        END
    );
ALTER TABLE public.withdrawal_requests ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE public.withdrawal_requests ALTER COLUMN status SET NOT NULL;

ALTER TABLE public.withdrawal_requests DROP CONSTRAINT IF EXISTS chk_withdrawal_requests_total_amount;
ALTER TABLE public.withdrawal_requests ADD CONSTRAINT chk_withdrawal_requests_total_amount CHECK (total_amount > 0);
ALTER TABLE public.withdrawal_requests ADD CONSTRAINT chk_withdrawal_requests_status 
    CHECK (status IN ('PENDING', 'APPROVED', 'PAID', 'REJECTED'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_teacher_active_withdrawal 
    ON public.withdrawal_requests (teacher_id) 
    WHERE status IN ('PENDING', 'APPROVED');

CREATE INDEX IF NOT EXISTS idx_withdrawal_requests_teacher ON public.withdrawal_requests(teacher_id);
CREATE INDEX IF NOT EXISTS idx_withdrawal_requests_status ON public.withdrawal_requests(status);

-- Money Ledger
CREATE TABLE IF NOT EXISTS public.money_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.users(id),
    entry_type VARCHAR(30) NOT NULL 
        CHECK (entry_type IN ('HOLD', 'RELEASE', 'WITHDRAW_LOCK', 'WITHDRAW_UNLOCK', 'PAYOUT', 'REFUND', 'COMMISSION')),
    amount NUMERIC(15, 0) NOT NULL,
    payment_id UUID REFERENCES public.payments(id),
    refund_id UUID REFERENCES public.refunds(id),
    withdrawal_id UUID REFERENCES public.withdrawal_requests(id),
    note TEXT,
    created_by UUID REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_money_ledger_teacher ON public.money_ledger(teacher_id);
CREATE INDEX IF NOT EXISTS idx_money_ledger_entry_type ON public.money_ledger(entry_type);
CREATE INDEX IF NOT EXISTS idx_money_ledger_created_at ON public.money_ledger(created_at);

CREATE OR REPLACE FUNCTION public.fn_prevent_money_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'BẢO MẬT: Bảng money_ledger là sổ cái chỉ-ghi-thêm (append-only). Tuyệt đối cấm UPDATE hoặc DELETE!';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_money_ledger_modification ON public.money_ledger;
CREATE TRIGGER trg_prevent_money_ledger_modification
BEFORE UPDATE OR DELETE ON public.money_ledger
FOR EACH ROW EXECUTE FUNCTION public.fn_prevent_money_ledger_modification();

-- System Config (bảng đã có trong schema nền ART_COUSE.sql -> ALTER TABLE bổ sung cột & bỏ cột thừa)
CREATE TABLE IF NOT EXISTS public.system_config (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES public.users(id)
);

ALTER TABLE public.system_config ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE public.system_config ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.system_config ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES public.users(id);
ALTER TABLE public.system_config DROP COLUMN IF EXISTS total_amount;
ALTER TABLE public.system_config DROP COLUMN IF EXISTS status;

INSERT INTO public.system_config (key, value, description)
VALUES 
    ('commission_rate_course', '0.20', 'Tỷ lệ hoa hồng khóa học video (20%)'),
    ('commission_rate_supply', '0.10', 'Tỷ lệ hoa hồng dụng cụ vẽ (10%)'),
    ('order_payment_timeout_minutes', '15', 'Thời hạn thanh toán đơn hàng trước khi bị hủy (15 phút)'),
    ('quote_payment_timeout_hours', '24', 'Thời hạn thanh toán sau khi phụ huynh chấp nhận báo giá customize (24 giờ)'),
    ('auto_confirm_delivery_days', '7', 'Mốc M ngày tự xác nhận nhận hàng sau khi Teacher báo giao hàng (7 ngày)'),
    ('auto_release_course_days', '', 'Mốc N ngày tự giải phóng tiền khóa học (mặc định rỗng/tắt)'),
    ('customize_auto_confirm_days', '', 'Mốc X ngày khóa customize tự giải phóng tiền nếu không khiếu nại (mặc định rỗng/tắt)'),
    ('default_shipping_fee', '30000', 'Phí vận chuyển cố định mặc định cho đơn dụng cụ (30.000 VND)')
ON CONFLICT (key) DO UPDATE 
SET description = EXCLUDED.description;

-- Moderation Reviews (bảng đã có trong schema nền ART_COUSE.sql -> ALTER TABLE đồng bộ kiểu & ràng buộc)
CREATE TABLE IF NOT EXISTS public.moderation_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL REFERENCES public.users(id),
    target_type VARCHAR(50) NOT NULL 
        CHECK (target_type IN ('TEACHER_PROFILE', 'COURSE', 'SUPPLY', 'CUSTOMIZE_REQUEST', 'REFUND')),
    target_id UUID NOT NULL,
    decision VARCHAR(50) NOT NULL 
        CHECK (decision IN ('APPROVED', 'REJECTED', 'FLAGGED', 'RESOLVED')),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.moderation_reviews ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE public.moderation_reviews ALTER COLUMN decision TYPE VARCHAR(50) USING decision::varchar(50);
ALTER TABLE public.moderation_reviews ALTER COLUMN decision SET NOT NULL;
ALTER TABLE public.moderation_reviews ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE public.moderation_reviews DROP CONSTRAINT IF EXISTS chk_moderation_reviews_target_type;
ALTER TABLE public.moderation_reviews ADD CONSTRAINT chk_moderation_reviews_target_type 
    CHECK (target_type IN ('TEACHER_PROFILE', 'COURSE', 'SUPPLY', 'CUSTOMIZE_REQUEST', 'REFUND'));

ALTER TABLE public.moderation_reviews DROP CONSTRAINT IF EXISTS chk_moderation_reviews_decision;
ALTER TABLE public.moderation_reviews ADD CONSTRAINT chk_moderation_reviews_decision 
    CHECK (decision IN ('APPROVED', 'REJECTED', 'FLAGGED', 'RESOLVED'));

CREATE INDEX IF NOT EXISTS idx_moderation_reviews_staff ON public.moderation_reviews(staff_id);
CREATE INDEX IF NOT EXISTS idx_moderation_reviews_target ON public.moderation_reviews(target_type, target_id);

-- Notifications
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    reference_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON public.notifications(recipient_id);
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON public.notifications(recipient_id, is_read);

-- Job Logs
CREATE TABLE IF NOT EXISTS public.scheduled_job_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    records_affected INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_job_logs_name_status ON public.scheduled_job_logs(job_name, status);

-- =====================================================================
-- 15. BẢNG KHÓA JOB PHÂN TÁN (SHEDLOCK) CHO SPRING BOOT
-- =====================================================================
CREATE TABLE IF NOT EXISTS public.shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
