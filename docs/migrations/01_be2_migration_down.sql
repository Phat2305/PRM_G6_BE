-- =====================================================================
-- BE2 DATABASE MIGRATION SCRIPT (DOWN / ROLLBACK)
-- Dự án: Nền tảng khóa học vẽ và dụng cụ vẽ (ART_COURSE - PRM_G6_BE)
-- Module: Backend 2 (BE2)
-- =====================================================================

DROP TABLE IF EXISTS public.scheduled_job_logs CASCADE;
DROP TABLE IF EXISTS public.shedlock CASCADE;
DROP TABLE IF EXISTS public.notifications CASCADE;
DROP TABLE IF EXISTS public.moderation_reviews CASCADE;

-- Không drop system_config hoàn toàn vì thuộc hệ thống chung, chỉ drop các khóa đã seed nếu cần
-- DROP TABLE IF EXISTS public.system_config CASCADE;

DROP TRIGGER IF EXISTS trg_prevent_money_ledger_modification ON public.money_ledger;
DROP FUNCTION IF EXISTS public.fn_prevent_money_ledger_modification();
DROP TABLE IF EXISTS public.money_ledger CASCADE;

DROP TABLE IF EXISTS public.withdrawal_requests CASCADE;
DROP TABLE IF EXISTS public.refund_items CASCADE;
DROP TABLE IF EXISTS public.refunds CASCADE;
DROP TABLE IF EXISTS public.webhook_logs CASCADE;
DROP TABLE IF EXISTS public.payments CASCADE;
DROP TABLE IF EXISTS public.order_details CASCADE;
DROP TABLE IF EXISTS public.orders CASCADE;
DROP TABLE IF EXISTS public.cart_items CASCADE;
DROP TABLE IF EXISTS public.carts CASCADE;

-- Bỏ các cột và ràng buộc đã thêm vào supplies
ALTER TABLE public.supplies DROP CONSTRAINT IF EXISTS chk_supplies_stock_non_negative;
ALTER TABLE public.supplies DROP CONSTRAINT IF EXISTS chk_supplies_available_stock;
ALTER TABLE public.supplies DROP COLUMN IF EXISTS reserved_quantity;
ALTER TABLE public.supplies DROP COLUMN IF EXISTS sold_quantity;
