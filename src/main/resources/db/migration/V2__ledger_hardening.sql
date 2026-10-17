-- =====================================================================
-- FLYWAY V2 MIGRATION: LEDGER HARDENING & ORDER_DETAILS SUB_TOTAL CHECK
-- =====================================================================

-- 1. Cập nhật hàm bảo vệ sổ cái và gắn thêm statement-level trigger BEFORE TRUNCATE (NFR-08)
CREATE OR REPLACE FUNCTION public.fn_prevent_money_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'BẢO MẬT: Bảng money_ledger là sổ cái chỉ-ghi-thêm (append-only). Tuyệt đối cấm UPDATE, DELETE hoặc TRUNCATE! (Operation: %)', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_money_ledger_truncate ON public.money_ledger;
CREATE TRIGGER trg_prevent_money_ledger_truncate
BEFORE TRUNCATE ON public.money_ledger
FOR EACH STATEMENT EXECUTE FUNCTION public.fn_prevent_money_ledger_modification();

-- 2. Ràng buộc CHECK (sub_total = unit_price * quantity) trên order_details (SHOP-03)
ALTER TABLE public.order_details DROP CONSTRAINT IF EXISTS chk_order_details_subtotal_math;
ALTER TABLE public.order_details ADD CONSTRAINT chk_order_details_subtotal_math
    CHECK (sub_total = unit_price * quantity);
