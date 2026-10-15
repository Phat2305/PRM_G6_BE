# Prompt 4 — Thanh toán, cấp quyền, giao hàng (Task B và C) [Spring Boot / Java 21]

Làm theo AGENTS.md. Phạm vi: PAY-01 đến PAY-11, FUL-01 đến FUL-08, BR-11.
Ngôn ngữ/Framework: Java 21 + Spring Boot 4.1.1. Test chạy trên DB dev (`DATABASE_URL_DEV`) hoặc Testcontainers, không đụng Supabase.

## NHIỆM VỤ
1. Tạo payment request và URL sang VNPay/MoMo (sandbox hoặc mock; đóng gói sau Spring Bean interface `PaymentGateway` để linh hoạt thay thế; khóa cổng lấy từ biến môi trường qua `@ConfigurationProperties` hoặc `@Value`, không hard-code).
2. Return URL và webhook (`@RestController`): xác thực chữ ký theo tài liệu từng cổng; idempotent theo (gateway, gateway_transaction_id); kiểm tra mã đơn, số tiền, trạng thái; sai thì giữ nguyên trạng thái đơn và ghi `webhook_logs` cảnh báo (PAY-01, 02, 09).
3. Thanh toán thành công trong MỘT `@Transactional`:
   - đơn → PAID; payment → HELD;
   - tính và lưu hoa hồng bằng `BigDecimal` theo loại dòng (phí ship và phí cổng tách khỏi cơ sở tính, FIN-12);
   - ghi sổ cái `money_ledger` loại HOLD;
   - dòng dụng cụ trong `order_details` → PENDING, giữ chỗ chuyển thành đã bán (`reserved_quantity` giảm, `sold_quantity` tăng).
   Sau commit (`TransactionSynchronizationManager.registerSynchronization` hoặc `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`) mới gọi cấp quyền học BE1 qua `IEnrollmentContract.grantAccess` cho đúng Child (PAY-03), có cơ chế retry `@Retryable` nếu BE1 lỗi; gửi thông báo qua `NotificationService` cho Parent và Teacher (PAY-06).
4. Truy vấn chủ động trạng thái giao dịch khi Parent quay lại app mà chưa có webhook (PAY-10).
5. Đối soát hằng ngày giữa đơn và giao dịch cổng (`@Scheduled` cron job), lưu danh sách lệch cho Staff (PAY-11).
6. Giao hàng (FUL - `FulfillmentService`):
   - Teacher xác nhận hoặc từ chối từng dòng dụng cụ kèm lý do;
   - nhập đơn vị vận chuyển, mã vận đơn, cập nhật trạng thái `delivery_status`;
   - Parent xem trạng thái và xác nhận đã nhận;
   - tự xác nhận sau M ngày từ khi báo đã giao (job `@Scheduled` + ShedLock);
   - Parent hủy dòng khi Teacher chưa xác nhận;
   - hủy/từ chối trả tồn kho và tạo yêu cầu hoàn tiền cho dòng đó;
   - giao thất bại/trả hàng chuyển cho Staff xử lý.
   Mỗi thay đổi gửi thông báo (FUL-04).

## TEST BẮT BUỘC (JUnit 5 + Mockito)
- Webhook gọi lặp (Idempotent).
- Webhook sai chữ ký (Signature verification).
- Webhook sai số tiền.
- BE1 cấp quyền lỗi rồi thử lại (`@Retryable`).
- Mọi bước chuyển trạng thái hợp lệ và không hợp lệ của dòng dụng cụ (`DeliveryStatus`).
- Job tự xác nhận chạy hai lần không tác dụng kép.

## KẾT QUẢ
Báo cáo: sơ đồ trạng thái đã cài, danh sách endpoint `@RestController`, giả định, câu hỏi mở.
