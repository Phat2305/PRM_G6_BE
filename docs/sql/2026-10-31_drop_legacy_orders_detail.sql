-- Only remove the legacy table. The current backend uses public.order_details.
-- RESTRICT stops this statement if a database object still depends on the old table.
BEGIN;

DO $$
BEGIN
    IF to_regclass('public.order_details') IS NULL THEN
        RAISE EXCEPTION 'public.order_details is missing; legacy table was not dropped';
    END IF;
END $$;

DROP TABLE IF EXISTS public.orders_detail RESTRICT;

COMMIT;
