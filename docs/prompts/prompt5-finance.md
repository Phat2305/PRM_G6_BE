# Prompt 5 — Escrow, hoa hồng, số dư, rút tiền, hoàn tiền (Task D) [Spring Boot / Java 21]

Làm theo AGENTS.md. Phạm vi: FIN-01 đến FIN-12, ADM-02, ADM-03, BR-07, BR-12, BR-15, mục 7.1, 7.2, #26.
Ngôn ngữ/Framework: Java 21 + Spring Boot 4.1.1. Test chạy trên DB dev (`DATABASE_URL_DEV`) hoặc Testcontainers, không đụng Supabase.

## NHIỆM VỤ
1. **Giải phóng tiền (`EscrowReleaseService`):** khoản thanh toán HELD → RELEASED khi MỌI dòng trong đơn đủ điều kiện (mục 7.1):
   - khóa học thường: học xong khóa (`CourseCompletedEvent`), hoặc Parent đánh giá (`CourseReviewedEvent`), hoặc mốc N ngày nếu Admin bật;
   - dụng cụ: Parent xác nhận đã nhận, hoặc M ngày sau khi Teacher báo đã giao;
   - customize: Parent xác nhận, hoặc X ngày không khiếu nại.
   Kích hoạt bằng Spring `@EventListener` và bằng `@Scheduled` job quét định kỳ kết hợp ShedLock. Khoản đang hoàn tiền hoặc khiếu nại thì KHÔNG giải phóng (FIN-10). Giải phóng trong `@Transactional` ghi sổ cái RELEASE và cộng số thực nhận vào số dư có thể rút của Teacher.
2. **Số dư của Teacher (`LedgerService`):** đang giữ, có thể rút, đang chờ rút, đã rút, kèm lịch sử (FIN-03). Tính toán trực tiếp bằng `BigDecimal` từ sổ cái `money_ledger` và các khoản thanh toán, không lưu số rời tránh lệch tiền.
3. **Yêu cầu rút tiền (`WithdrawalService`):**
   - Teacher gửi (số tiền ≤ số dư có thể rút, tối đa 1 yêu cầu PENDING/APPROVED theo BR-15); ghi sổ cái WITHDRAW_LOCK khóa số tiền;
   - Admin duyệt hoặc từ chối kèm lý do (từ chối ghi sổ cái WITHDRAW_UNLOCK trả lại số dư);
   - Staff chuyển khoản thủ công rồi đánh dấu PAID kèm mã giao dịch và ảnh chứng từ (ghi sổ cái PAYOUT).
   Chống duyệt/chi hai lần; kiểm tra chặt chẽ ở tầng `@Service` (FIN-04, 05, 06).
4. **Hoàn tiền (`RefundService`)** theo từng dòng chi tiết `order_details` gắn với `payments`:
   - tổng hoàn ≤ số đã thanh toán;
   - thu hồi quyền học khi hoàn khóa học (gọi `IEnrollmentContract.revokeAccess`);
   - trả tồn kho khi cần; ghi sổ cái REFUND;
   - Staff ghi nhận chuyển khoản hoàn kèm mã giao dịch (FIN-07 đến FIN-09);
   - phần đã hoàn không cộng vào số dư có thể rút.
5. **Thông báo (`NotificationService`):** Teacher (rút tiền được duyệt/bị từ chối/đã chi), Parent (đã hoàn tiền) (FIN-11).
6. **Cấu hình (`ConfigService`):** hoa hồng và các mốc thời gian đọc từ `system_config`, chỉ Admin sửa được (ADM-02); thay đổi cấu hình ghi nhật ký `moderation_reviews`.

## TEST BẮT BUỘC (JUnit 5 + Mockito)
- Giải phóng khi chỉ một phần dòng đủ điều kiện (không được giải phóng, #26).
- Rút vượt số dư khả dụng (`Money`).
- Hai yêu cầu rút tiền đồng thời của 1 Teacher (BR-15).
- Từ chối trả số dư chính xác.
- Hoàn vượt số tiền đã thanh toán.
- Sổ cái `money_ledger` không UPDATE/DELETE được (Trigger kiểm chứng).
- Tổng số dư khớp tổng sổ cái bất biến.

## KẾT QUẢ
Báo cáo: công thức tính số dư, sơ đồ trạng thái, giả định, câu hỏi mở.
