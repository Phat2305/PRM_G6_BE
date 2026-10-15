# Prompt 6 — Staff, Admin, thông báo và job nền (Task E và F) [Spring Boot / Java 21]

Làm theo AGENTS.md. Phạm vi: STF-01 đến STF-07, ADM-01 đến ADM-06, ACC-13, NFR-08, NFR-16, NFR-19 và nhóm Thông báo/job nền.
Ngôn ngữ/Framework: Java 21 + Spring Boot 4.1.1. Test chạy trên DB dev (`DATABASE_URL_DEV`) hoặc Testcontainers, không đụng Supabase.

## NHIỆM VỤ
1. **Nhật ký duyệt (`ModerationService`):** mỗi quyết định duyệt/từ chối hồ sơ Teacher, khóa học (kể cả customize và bản sửa), dụng cụ, bài đăng yêu cầu được lưu vào `moderation_reviews` (STF-07). Module BE1 gọi qua Spring interface chung; BE2 cung cấp `@RestController` ghi và xem lịch sử.
2. **Staff:** ẩn/gỡ nội dung vi phạm, tiếp nhận khiếu nại, xử lý chuyển khoản/hoàn tiền (liên kết Prompt 5), xử lý giao dịch lệch (PAY-11). Staff chỉ đọc cuộc trao đổi khi xử lý khiếu nại liên quan (MSG-06).
3. **Admin:** tạo/khóa/phân quyền Staff; cấu hình hoa hồng và mốc thời gian qua `system_config`; duyệt rút tiền; thống kê tiền (doanh thu, đang giữ, có thể rút, chờ rút, đã chi, đã hoàn, hoa hồng) và thống kê vận hành; xem nhật ký thao tác tiền (ADM-01 đến ADM-06).
4. **Dịch vụ thông báo dùng chung (`NotificationService`):** (Push + Email) có hàng đợi `@Async` và cơ chế retry có kiểm soát. Sự kiện: thanh toán thành công, bài nộp mới, kết quả chấm, tin nhắn mới, thay đổi giao hàng, hoàn tiền, rút tiền.
5. **Gom mọi scheduled job BE2 vào một nơi:** gom các method `@Scheduled` vào package `jobs`: hết hạn đơn, tự xác nhận nhận hàng, tự xác nhận hoàn thành customize, giải phóng tiền, hủy chấp nhận báo giá quá hạn thanh toán, đối soát. Khóa job bằng ShedLock (`@SchedulerLock`) trên PostgreSQL, ghi nhật ký `scheduled_job_logs` mỗi lần chạy, chạy lại không tác dụng kép.
6. **Log tập trung và cảnh báo:** khi webhook lỗi, hàng đợi video ùn, tỷ lệ lỗi API tăng (NFR-16); kiểm tra sao lưu/khôi phục DB ở mức script và tài liệu (NFR-15).

## TEST BẮT BUỘC (JUnit 5 + Spring Security Test)
- Phân quyền từng vai trò bằng `@WithMockUser` (Staff không vào API Admin và ngược lại; Teacher không xem số dư người khác).
- Job chạy an toàn khi nhiều instance (kiểm chứng cơ chế lock của ShedLock).
- Quyết định duyệt luôn sinh ra bản ghi trong `moderation_reviews`.

## KẾT QUẢ
Tạo `docs/be2-final-report.md`: ma trận mã SRS → file/endpoint/test, mục chưa làm, các mục #1 đến #27 vẫn mở và giá trị tạm dùng.
