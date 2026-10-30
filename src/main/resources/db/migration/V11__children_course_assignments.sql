-- SHOP-04/CUS-12: child assignments live in children_course; a customize course is unknown at payment time.
CREATE TABLE IF NOT EXISTS public.children_course (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES public.orders(id),
    course_id UUID REFERENCES public.courses(id),
    child_id UUID NOT NULL,
    price NUMERIC(12, 2) NOT NULL DEFAULT 0
);

-- The course does not exist yet when a customize order is paid.
ALTER TABLE public.children_course ALTER COLUMN course_id DROP NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.children_course
        GROUP BY order_id, course_id, child_id HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate children_course assignments exist. Resolve them before applying V11.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_children_course_assigned
    ON public.children_course (order_id, course_id, child_id) WHERE course_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_children_course_pending_customize
    ON public.children_course (order_id, child_id) WHERE course_id IS NULL;

INSERT INTO public.children_course (id, order_id, course_id, child_id, price)
SELECT gen_random_uuid(), d.order_id,
       CASE WHEN d.customize_request_id IS NOT NULL AND d.delivery_status = 'COMPLETED'
            THEN c.id ELSE d.course_id END,
       d.child_id, d.unit_price
FROM public.order_details d
LEFT JOIN public.courses c ON c.customize_request_id = d.customize_request_id
WHERE d.child_id IS NOT NULL
  AND (d.course_id IS NOT NULL OR d.customize_request_id IS NOT NULL)
ON CONFLICT DO NOTHING;
