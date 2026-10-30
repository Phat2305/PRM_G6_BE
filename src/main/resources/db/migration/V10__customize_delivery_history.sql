-- CUS-12: one reusable course per request and an audit row for each approved delivery.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.courses
        WHERE customize_request_id IS NOT NULL
        GROUP BY customize_request_id HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate customize courses exist. Resolve them before applying V10.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_courses_customize_request
    ON public.courses (customize_request_id)
    WHERE customize_request_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.customize_delivery_history (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES public.customize_requests(id),
    course_id UUID NOT NULL REFERENCES public.courses(id),
    course_name VARCHAR(255) NOT NULL,
    lesson_count INTEGER NOT NULL CHECK (lesson_count >= 0),
    delivered_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    result VARCHAR(20),
    reason TEXT,
    CONSTRAINT chk_customize_delivery_resolution CHECK (
        (resolved_at IS NULL AND result IS NULL) OR
        (resolved_at IS NOT NULL AND result IN ('CONFIRMED', 'REJECTED', 'REVISED'))
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_customize_delivery_open
    ON public.customize_delivery_history (request_id) WHERE resolved_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_customize_delivery_request_time
    ON public.customize_delivery_history (request_id, delivered_at DESC);

-- Existing current deliveries can be reconstructed; earlier revisions had no durable snapshot.
INSERT INTO public.customize_delivery_history
    (id, request_id, course_id, course_name, lesson_count, delivered_at, resolved_at, result)
SELECT gen_random_uuid(), r.id, c.id, COALESCE(c.name, ''),
       (SELECT COUNT(*)::integer FROM public.lessons l WHERE l.course_id = c.id),
       MAX(d.delivered_at),
       CASE WHEN UPPER(r.status) = 'COMPLETED'
            THEN COALESCE(MAX(d.confirmed_delivered_at), MAX(d.delivered_at)) END,
       CASE WHEN UPPER(r.status) = 'COMPLETED' THEN 'CONFIRMED' END
FROM public.customize_requests r
JOIN public.courses c ON c.customize_request_id = r.id
JOIN public.order_details d ON d.customize_request_id = r.id
WHERE UPPER(r.status) IN ('DELIVERED', 'COMPLETED')
  AND d.delivered_at IS NOT NULL
GROUP BY r.id, r.status, c.id, c.name;
