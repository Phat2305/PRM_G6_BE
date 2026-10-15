-- =====================================================================
-- SEED SAMPLE DATA SCRIPT (BE2)
-- Kịch bản dữ liệu mẫu:
-- - 1 Admin, 1 Staff, 1 Parent (có 1 Child), 2 Teachers
-- - Khóa học & Dụng cụ vẽ của 2 Teachers
-- - Đơn hàng mẫu (Khóa học và Dụng cụ tách theo Teacher)
-- - Khoản thanh toán (Payment HELD) và Sổ cái tiền (Money Ledger HOLD)
-- =====================================================================

DO $$
DECLARE
    -- User IDs
    v_admin_id UUID := '11111111-1111-1111-1111-111111111111';
    v_staff_id UUID := '22222222-2222-2222-2222-222222222222';
    v_parent_id UUID := '33333333-3333-3333-3333-333333333333';
    v_child_id UUID := '44444444-4444-4444-4444-444444444444';
    v_teacher1_id UUID := '55555555-5555-5555-5555-555555555555';
    v_teacher2_id UUID := '66666666-6666-6666-6666-666666666666';

    -- Product IDs
    v_course1_id UUID := 'aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa';
    v_course2_id UUID := 'aaaaaaaa-2222-2222-2222-aaaaaaaaaaaa';
    v_supply1_id UUID := 'bbbbbbbb-1111-1111-1111-bbbbbbbbbbbb';
    v_supply2_id UUID := 'bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb';

    -- Order IDs
    v_order1_id UUID := 'cccccccc-1111-1111-1111-cccccccccccc';
    v_order2_id UUID := 'cccccccc-2222-2222-2222-cccccccccccc';
    v_payment1_id UUID := 'dddddddd-1111-1111-1111-dddddddddddd';
    v_cart_id UUID := 'eeeeeeee-1111-1111-1111-eeeeeeeeeeee';
BEGIN
    -- 1. USERS
    INSERT INTO public.users (id, username, password, role, status)
    VALUES
        (v_admin_id, 'admin@artcourse.com', '$2a$10$hashedpass1', 'ADMIN', 'ACTIVE'),
        (v_staff_id, 'staff@artcourse.com', '$2a$10$hashedpass2', 'STAFF', 'ACTIVE'),
        (v_parent_id, 'parent@artcourse.com', '$2a$10$hashedpass3', 'PARENT', 'ACTIVE'),
        (v_child_id, 'child@artcourse.com', '$2a$10$hashedpass4', 'CHILD', 'ACTIVE'),
        (v_teacher1_id, 'teacher1@artcourse.com', '$2a$10$hashedpass5', 'TEACHER', 'ACTIVE'),
        (v_teacher2_id, 'teacher2@artcourse.com', '$2a$10$hashedpass6', 'TEACHER', 'ACTIVE')
    ON CONFLICT (id) DO NOTHING;

    -- 2. PROFILES
    INSERT INTO public.parent_profiles (user_id, full_name, shipping_address)
    VALUES (v_parent_id, 'Nguyễn Văn Phụ Huynh', 'Số 123 Đường Cầu Giấy, Hà Nội')
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO public.child_profiles (user_id, parent_id, display_name, birth_year)
    VALUES (v_child_id, v_parent_id, 'Bé Bo', 2018)
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO public.teacher_profiles (user_id, full_name, certificates_text, bank_account_info)
    VALUES 
        (v_teacher1_id, 'Họa Sĩ Trần Mỹ Thuật', 'Cử nhân ĐH Mỹ thuật Việt Nam', 'MBBank - 999988887777 - TRAN MY THUAT'),
        (v_teacher2_id, 'Giáo Viên Lê Màu Nước', 'Thạc sĩ Mỹ thuật Ứng dụng', 'Vietcombank - 001100223344 - LE MAU NUOC')
    ON CONFLICT (user_id) DO NOTHING;

    -- 3. COURSES & SUPPLIES (BE1)
    INSERT INTO public.courses (id, teacher_id, name, description, price, status)
    VALUES
        (v_course1_id, v_teacher1_id, 'Học vẽ cơ bản cho bé 6-10 tuổi', 'Khóa học vẽ nét và màu sắc cơ bản', 300000, 'PUBLISHED'),
        (v_course2_id, v_teacher2_id, 'Nghệ thuật Màu Nước Nâng Cao', 'Kỹ thuật loang màu và dựng hình', 500000, 'PUBLISHED')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.supplies (id, teacher_id, name, description, price, stock_quantity, reserved_quantity, sold_quantity, status)
    VALUES
        (v_supply1_id, v_teacher1_id, 'Bộ bút chì màu và sổ vẽ A4', '12 màu chuyên dụng cho bé', 120000, 50, 2, 0, 'ACTIVE'),
        (v_supply2_id, v_teacher2_id, 'Hộp màu nước Gouache 24 màu', 'Màu nước cao cấp độ phủ mịn', 250000, 30, 0, 0, 'ACTIVE')
    ON CONFLICT (id) DO NOTHING;

    -- 4. CARTS & CART ITEMS
    INSERT INTO public.carts (id, parent_id)
    VALUES (v_cart_id, v_parent_id)
    ON CONFLICT (parent_id) DO NOTHING;

    -- 5. ORDERS & ORDER DETAILS (Tách mỗi đơn đúng 1 Teacher)
    -- Đơn 1: Thuộc Teacher 1 gồm: Khóa học cơ bản (gán cho Bé Bo) + Bộ bút chì màu (vận chuyển)
    INSERT INTO public.orders (id, parent_id, teacher_id, total_amount, shipping_fee, status)
    VALUES (v_order1_id, v_parent_id, v_teacher1_id, 450000, 30000, 'PAID')
    ON CONFLICT (id) DO NOTHING;

    -- Chi tiết đơn 1: Dòng khóa học
    INSERT INTO public.order_details (
        id, order_id, product_type, course_id, supply_id, child_id, product_name,
        unit_price, quantity, sub_total, commission_rate, commission_amount, net_amount
    )
    VALUES (
        gen_random_uuid(), v_order1_id, 'COURSE', v_course1_id, NULL, v_child_id, 'Học vẽ cơ bản cho bé 6-10 tuổi',
        300000, 1, 300000, 0.20, 60000, 240000
    )
    ON CONFLICT DO NOTHING;

    -- Chi tiết đơn 1: Dòng dụng cụ vẽ
    INSERT INTO public.order_details (
        id, order_id, product_type, course_id, supply_id, child_id, product_name,
        unit_price, quantity, sub_total, commission_rate, commission_amount, net_amount,
        delivery_status, shipping_provider, tracking_code
    )
    VALUES (
        gen_random_uuid(), v_order1_id, 'SUPPLY', NULL, v_supply1_id, NULL, 'Bộ bút chì màu và sổ vẽ A4',
        120000, 1, 120000, 0.10, 12000, 108000,
        'SHIPPING', 'Giao Hàng Nhanh', 'GHN987654321VN'
    )
    ON CONFLICT DO NOTHING;

    -- 6. PAYMENT (Khoản thanh toán của Đơn 1 đang ở trạng thái HELD)
    -- Tổng: 450.000 VND (300k khóa + 120k dụng cụ + 30k ship)
    -- Hoa hồng sàn: 60k (khóa 20%) + 12k (dụng cụ 10%) = 72k
    -- Net Teacher thực nhận khi giải phóng: 450.000 - 72.000 = 378.000 VND
    INSERT INTO public.payments (
        id, order_id, teacher_id, gateway, gateway_transaction_id, amount,
        commission_amount, net_amount, gateway_fee, status, paid_at
    )
    VALUES (
        v_payment1_id, v_order1_id, v_teacher1_id, 'VNPAY', 'VNP_20261014_998877',
        450000, 72000, 378000, 0, 'HELD', CURRENT_TIMESTAMP
    )
    ON CONFLICT (id) DO NOTHING;

    -- 7. MONEY LEDGER (Ghi bút toán HOLD vào sổ cái chỉ-ghi-thêm)
    INSERT INTO public.money_ledger (
        teacher_id, entry_type, amount, payment_id, note
    )
    VALUES (
        v_teacher1_id, 'HOLD', 378000, v_payment1_id, 'Tạm giữ tiền đơn hàng #cccccccc-1111 sau khi thanh toán thành công'
    );

    -- Đơn 2: Thuộc Teacher 2 (Đang chờ thanh toán PENDING_PAYMENT)
    INSERT INTO public.orders (id, parent_id, teacher_id, total_amount, shipping_fee, status, expired_at)
    VALUES (v_order2_id, v_parent_id, v_teacher2_id, 280000, 30000, 'PENDING_PAYMENT', CURRENT_TIMESTAMP + INTERVAL '15 minutes')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.order_details (
        id, order_id, product_type, course_id, supply_id, child_id, product_name,
        unit_price, quantity, sub_total, commission_rate, commission_amount, net_amount,
        delivery_status
    )
    VALUES (
        gen_random_uuid(), v_order2_id, 'SUPPLY', NULL, v_supply2_id, NULL, 'Hộp màu nước Gouache 24 màu',
        250000, 1, 250000, 0.10, 25000, 225000,
        'PENDING'
    )
    ON CONFLICT DO NOTHING;

END $$;
