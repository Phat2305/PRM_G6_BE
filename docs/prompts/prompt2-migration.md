# Prompt 2 — Migration database

Làm theo AGENTS.md và báo cáo `docs/audit/be2-gap-report.md`, cộng các quyết định tôi ghi trong `docs/decisions.md`.

**Bắt buộc:** chỉ chạy trên database dev (`DATABASE_URL_DEV`). Nếu biến này chưa có thì chỉ VIẾT file migration, KHÔNG chạy, KHÔNG đụng Supabase trong `DATABASE_URL`. Nếu audit cho thấy có dữ liệu thật cần giữ, DỪNG và hỏi.

## NHIỆM VỤ
Viết migration (dùng công cụ repo đang dùng; nếu chưa có thì đề xuất rồi chờ duyệt) đưa các bảng BE2 về đúng ERD đích + SRS. Chỉ động vào bảng BE2 sở hữu; thay đổi bảng BE1 chỉ ghi vào `docs/handoff-be1.md`.

Thay đổi cần có (điều chỉnh theo audit, ghi rõ khi lệch):
- **orders:** thêm `teacher_id` (FK), `expired_at`; đổi `shipping_cost` → `shipping_fee`; bỏ `child_id` và `shipped_at` nếu ERD đích không có. Trạng thái: PENDING_PAYMENT, PAID, EXPIRED, CANCELLED.
- **order_details:** `course_id` và `supply_id` nullable với CHECK đúng một trong hai; snapshot tên sản phẩm, unit_price, quantity, sub_total; tỷ lệ và số hoa hồng áp dụng của dòng; `child_id` cho dòng khóa học (nếu audit chọn phương án này); thông tin vận chuyển và trạng thái giao hàng chỉ cho dòng dụng cụ (PENDING, CONFIRMED, SHIPPING, DELIVERED, COMPLETED, CANCELLED, REJECTED, DELIVERY_FAILED, REFUNDED).
- **Tồn kho dụng cụ:** cách ghi giữ chỗ và đã bán (cột hoặc bảng theo audit), CHECK không âm.
- **carts, cart_items** (nếu chọn lưu server): UNIQUE theo (cart, sản phẩm), kiểm tra loại sản phẩm.
- **payment:** `teacher_id`, mã tham chiếu cổng, payment_url hoặc tương đương, `commission_amount`, `net_amount`, phí cổng (nullable), trạng thái PENDING/HELD/RELEASED/FAILED/REFUNDED, `released_at`; UNIQUE (gateway, gateway_transaction_id); mỗi đơn đúng một khoản thanh toán.
- **webhook_logs:** payload, chữ ký hợp lệ hay không, kết quả xử lý.
- **refunds + refund_items** (refund_id, order_detail_id, amount): gắn payment, hoàn theo dòng, thông tin tài khoản nhận, người duyệt/chi, mã giao dịch.
- **withdrawal_requests:** status là chuỗi PENDING/APPROVED/PAID/REJECTED; thêm lý do từ chối, người duyệt, người chi, mã giao dịch, ảnh chứng từ; unique index một phần: mỗi Teacher tối đa 1 yêu cầu PENDING/APPROVED.
- **money_ledger** (chỉ ghi thêm): loại (HOLD, RELEASE, WITHDRAW_LOCK, WITHDRAW_UNLOCK, PAYOUT, REFUND, COMMISSION), teacher_id, payment/refund/withdrawal liên quan, số tiền, thời điểm, người thực hiện. Chặn UPDATE/DELETE bằng trigger.
- **system_config:** làm sạch (bỏ cột không thuộc cấu hình); seed khóa: tỷ lệ hoa hồng theo loại, hạn thanh toán đơn, hạn thanh toán báo giá, M, N, X, phí ship mặc định nếu cần.
- **moderation_reviews:** đủ cột theo STF-07 (người quyết định, loại, mã đối tượng, quyết định, lý do, thời điểm).
- **notifications** và bảng khóa job nếu repo chưa có.
- Index cho các truy vấn chính (đơn theo parent/teacher/status, payment theo order, ledger theo teacher).

Cung cấp: migration up (và down nếu công cụ hỗ trợ), kiểm tra chạy được trên DB trống, script seed dữ liệu mẫu (1 Parent, 2 Teacher, dụng cụ, khóa học, đơn mẫu), và test kiểm tra các CHECK/UNIQUE chính.

## KẾT QUẢ
Báo cáo `docs/migration-report.md` liệt kê từng thay đổi và mã SRS tương ứng. Dừng chờ duyệt.
