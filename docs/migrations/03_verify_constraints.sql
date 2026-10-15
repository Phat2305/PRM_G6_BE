-- =====================================================================
-- CONSTRAINT VERIFICATION AND INTEGRITY TEST SUITE (BE2)
-- Chạy script này trên PostgreSQL dev để xác minh các ràng buộc CHECK,
-- UNIQUE, Trigger chống sửa sổ cái, và tính toàn vẹn dữ liệu.
-- =====================================================================

DO $$
DECLARE
    v_test_passed BOOLEAN;
    v_err_occurred BOOLEAN;
    v_parent_id UUID := '33333333-3333-3333-3333-333333333333';
    v_teacher1_id UUID := '55555555-5555-5555-5555-555555555555';
    v_child_id UUID := '44444444-4444-4444-4444-444444444444';
    v_course1_id UUID := 'aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa';
    v_supply1_id UUID := 'bbbbbbbb-1111-1111-1111-bbbbbbbbbbbb';
    v_order1_id UUID := 'cccccccc-1111-1111-1111-cccccccccccc';
    v_cart_id UUID;
    v_ledger_id UUID;
BEGIN
    RAISE NOTICE '>>> BẮT ĐẦU KIỂM TRA BỘ RÀNG BUỘC (TEST SUITE) BE2 <<<';

    SELECT id INTO v_cart_id FROM public.carts WHERE parent_id = v_parent_id LIMIT 1;

    -- =================================================================
    -- TEST 1: CHỐNG THÊM TRÙNG 1 KHÓA HỌC CHO CÙNG 1 CHILD VÀO GIỎ (SHOP-04)
    -- =================================================================
    v_err_occurred := FALSE;
    BEGIN
        -- Insert lần 1
        INSERT INTO public.cart_items (cart_id, product_type, course_id, child_id, quantity)
        VALUES (v_cart_id, 'COURSE', v_course1_id, v_child_id, 1);

        -- Insert lần 2 (Cùng cart, cùng course, cùng child -> Phải báo lỗi Unique)
        INSERT INTO public.cart_items (cart_id, product_type, course_id, child_id, quantity)
        VALUES (v_cart_id, 'COURSE', v_course1_id, v_child_id, 1);
    EXCEPTION WHEN unique_violation THEN
        v_err_occurred := TRUE;
    END;

    IF v_err_occurred THEN
        RAISE NOTICE '[PASSED] Test 1: Chặn mua trùng khóa cho cùng Child thành công (Unique Violation).';
    ELSE
        RAISE EXCEPTION '[FAILED] Test 1: Hệ thống cho phép thêm trùng khóa học cho cùng một Child vào giỏ!';
    END IF;

    -- Clean up cart item test
    DELETE FROM public.cart_items WHERE cart_id = v_cart_id AND course_id = v_course1_id;

    -- =================================================================
    -- TEST 2: CHECK RÀNG BUỘC LOẠI TRỪ CỦA ORDER_DETAILS (CHỈ KHÓA HOẶC CHỈ DỤNG CỤ)
    -- =================================================================
    v_err_occurred := FALSE;
    BEGIN
        -- Cố tình vi phạm: Cung cấp cả course_id và supply_id
        INSERT INTO public.order_details (
            order_id, product_type, course_id, supply_id, child_id, product_name,
            unit_price, quantity, sub_total
        ) VALUES (
            v_order1_id, 'COURSE', v_course1_id, v_supply1_id, v_child_id, 'Invalid Item',
            100000, 1, 100000
        );
    EXCEPTION WHEN check_violation THEN
        v_err_occurred := TRUE;
    END;

    IF v_err_occurred THEN
        RAISE NOTICE '[PASSED] Test 2: Ràng buộc loại trừ (CHECK course_id vs supply_id) hoạt động chính xác.';
    ELSE
        RAISE EXCEPTION '[FAILED] Test 2: Cho phép lưu dòng chi tiết vừa có course_id vừa có supply_id!';
    END IF;

    -- =================================================================
    -- TEST 3: CHẶN UPDATE VÀ DELETE TRÊN SỔ CÁI BẤT BIẾN MONEY_LEDGER (NFR-08)
    -- =================================================================
    -- Insert 1 bản ghi sổ cái test
    INSERT INTO public.money_ledger (teacher_id, entry_type, amount, note)
    VALUES (v_teacher1_id, 'COMMISSION', 50000, 'Test immutable ledger')
    RETURNING id INTO v_ledger_id;

    -- Thử UPDATE (Phải bị trigger raise exception)
    v_err_occurred := FALSE;
    BEGIN
        UPDATE public.money_ledger SET amount = 999999 WHERE id = v_ledger_id;
    EXCEPTION WHEN OTHERS THEN
        v_err_occurred := TRUE;
    END;

    IF v_err_occurred THEN
        RAISE NOTICE '[PASSED] Test 3a: Trigger chặn UPDATE trên money_ledger thành công.';
    ELSE
        RAISE EXCEPTION '[FAILED] Test 3a: Cho phép UPDATE trên money_ledger!';
    END IF;

    -- Thử DELETE (Phải bị trigger raise exception)
    v_err_occurred := FALSE;
    BEGIN
        DELETE FROM public.money_ledger WHERE id = v_ledger_id;
    EXCEPTION WHEN OTHERS THEN
        v_err_occurred := TRUE;
    END;

    IF v_err_occurred THEN
        RAISE NOTICE '[PASSED] Test 3b: Trigger chặn DELETE trên money_ledger thành công.';
    ELSE
        RAISE EXCEPTION '[FAILED] Test 3b: Cho phép DELETE trên money_ledger!';
    END IF;

    -- =================================================================
    -- TEST 4: CHẶN 2 YÊU CẦU RÚT TIỀN HOẠT ĐỘNG ĐỒNG THỜI CỦA 1 TEACHER (BR-15)
    -- =================================================================
    v_err_occurred := FALSE;
    BEGIN
        -- Request 1: PENDING
        INSERT INTO public.withdrawal_requests (teacher_id, total_amount, bank_info, status)
        VALUES (v_teacher1_id, 200000, 'MBBank - 999988887777', 'PENDING');

        -- Request 2: PENDING (Cùng Teacher -> Phải báo lỗi Unique Partial Index)
        INSERT INTO public.withdrawal_requests (teacher_id, total_amount, bank_info, status)
        VALUES (v_teacher1_id, 100000, 'MBBank - 999988887777', 'PENDING');
    EXCEPTION WHEN unique_violation THEN
        v_err_occurred := TRUE;
    END;

    IF v_err_occurred THEN
        RAISE NOTICE '[PASSED] Test 4: Ràng buộc BR-15 (tối đa 1 yêu cầu rút tiền active) hoạt động chính xác.';
    ELSE
        RAISE EXCEPTION '[FAILED] Test 4: Cho phép tạo nhiều hơn 1 yêu cầu rút tiền active cho cùng 1 Teacher!';
    END IF;

    -- Clean up test withdrawal
    DELETE FROM public.withdrawal_requests WHERE teacher_id = v_teacher1_id;

    -- =================================================================
    -- TEST 5: CHECK TỒN KHO KHÔNG ÂM & KHÔNG GIỮ CHỖ VƯỢT TỒN KHO THỰC
    -- =================================================================
    v_err_occurred := FALSE;
    BEGIN
        -- Cố tình update reserved_quantity > stock_quantity
        UPDATE public.supplies 
        SET reserved_quantity = 999 
        WHERE id = v_supply1_id AND stock_quantity = 50;
    EXCEPTION WHEN check_violation THEN
        v_err_occurred := TRUE;
    END;

    IF v_err_occurred THEN
        RAISE NOTICE '[PASSED] Test 5: Ràng buộc stock_quantity >= reserved_quantity hoạt động chính xác.';
    ELSE
        RAISE EXCEPTION '[FAILED] Test 5: Cho phép giữ chỗ vượt số lượng tồn kho!';
    END IF;

    RAISE NOTICE '>>> TẤT CẢ 5 BỘ TEST RÀNG BUỘC BE2 ĐÃ VƯỢT QUA THÀNH CÔNG! <<<';
END $$;
