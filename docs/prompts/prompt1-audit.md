# Prompt 1 — Audit (chỉ đọc, KHÔNG sửa code)

Làm theo AGENTS.md. Đọc cả thư mục `doc/` và `docs/`. Tuyệt đối không đọc nội dung `.env`, không kết nối database, không chạy migration, không sửa code.

## NHIỆM VỤ
1. Xác định stack thực tế: ngôn ngữ/phiên bản, framework, build tool, công cụ migration (nếu có), kiểu auth, cách truy cập DB (pg thuần/ORM), cách tổ chức module, quy ước đặt tên, format response/lỗi, test framework, cách chạy dự án. Liệt kê file làm bằng chứng.
2. Liệt kê module/controller/service đã có và ánh xạ vào mã SRS của BE2 (SHOP, PAY, FUL, FIN, STF, ADM, NFR). Phân loại từng mục: GIỮ / MỞ RỘNG / VIẾT LẠI / CHƯA CÓ, kèm lý do ngắn.
3. So sánh code, schema hiện hành (`docs/ART_COUSE_old.sql`) và ERD đích. Lập bảng khác biệt theo bảng/cột (tên bảng, kiểu, null, khóa, CHECK/UNIQUE, ON DELETE). Chú ý: orders, order_details, payment, refunds, withdrawal_requests, supplies/suppliers, system_config, conversations.
4. Chỉ ra phần code/mô hình cũ mâu thuẫn quyết định đã chốt (đơn nhiều giáo viên, hoàn tiền theo payment, v.v.).
5. Rà bảo mật tiền (chỉ đọc code): webhook, idempotency, transaction, phân quyền.
6. Liệt kê chỗ ERD còn mơ hồ cần quyết định, kèm đề xuất:
   (a) Child được gán khóa học lưu ở đâu khi tạo đơn: order_details hay children_course;
   (b) đơn customize liên kết yêu cầu customize thế nào khi khóa chưa tồn tại (#27);
   (c) tồn kho giữ chỗ/đã bán lưu ở cột hay bảng riêng;
   (d) giỏ hàng lưu server hay app;
   (e) có database dev riêng chưa (DATABASE_URL_DEV)?

## KẾT QUẢ
Ghi vào:
- `docs/audit/be2-gap-report.md` (đủ các mục trên)
- `docs/open-questions.md`
- `docs/handoff-be1.md`

Sau đó DỪNG và chờ.
