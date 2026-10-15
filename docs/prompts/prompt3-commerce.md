# Prompt 3 — Cửa hàng, giỏ hàng, đơn hàng (Task A) [Spring Boot / Java 21]

Làm theo AGENTS.md. Phạm vi: SHOP-01 đến SHOP-10, PAY-07, PAY-08, BR-04, BR-11, FIN-12 (phần phí ship).
Ngôn ngữ/Framework: Java 21 + Spring Boot 4.1.1. Test chạy trên DB dev (`DATABASE_URL_DEV`) hoặc Testcontainers, không đụng Supabase.

## NHIỆM VỤ
1. API duyệt/tìm kiếm khóa học và dụng cụ (SHOP-01; đọc dữ liệu BE1 qua CourseRepository/SupplyRepository; học cụ gợi ý hiển thị theo bài học dùng đến).
2. Giỏ hàng: thêm/sửa/xóa (`CartController`, `CartService`); chỉ nhận sản phẩm đang công khai và còn tồn kho khả dụng; chặn mua trùng một khóa cho cùng một Child (SHOP-04); yêu cầu Parent đã có ít nhất một Child khi mua khóa (ACC-04).
3. Tạo đơn từ giỏ (`OrderService.createOrdersFromCheckout`): tách thành mỗi Teacher một đơn (SHOP-05, BR-04, #25); với đơn có dụng cụ, tính phí ship theo quy tắc Teacher đặt phí cố định cho đơn có dụng cụ (#8); lưu snapshot tên, đơn giá, số lượng, Child được gán (SHOP-03) vào bảng `order_details`; tổng tiền = tổng dòng + phí ship. Tiền tệ tính bằng `BigDecimal`, làm tròn `RoundingMode.HALF_UP`.
4. Giữ chỗ tồn kho khi tạo đơn trong cùng `@Transactional`, chống bán vượt tồn khi nhiều request đồng thời bằng `@Lock(LockModeType.PESSIMISTIC_WRITE)` hoặc update có điều kiện; không đủ hàng thì rollback không tạo đơn (PAY-07).
5. Job hết hạn đơn: đơn PENDING_PAYMENT quá hạn → EXPIRED và trả lại tồn kho; chạy lại không trả hai lần (PAY-08). Khóa job bằng ShedLock (`@SchedulerLock`).
6. Lịch sử đơn và chi tiết cho Parent (SHOP-08); Teacher xem đơn có dụng cụ của mình (FUL-01).
7. Đánh giá khóa học: chỉ Parent có Child đã được gán khóa đó, 1-5 sao, chống đánh giá trùng (SHOP-09). Phát sự kiện `CourseReviewedEvent` qua Spring ApplicationEventPublisher cho module dòng tiền.
8. Xem thử miễn phí (SHOP-10): chỉ cần Parent đã đăng nhập, không tạo tiến độ (LRN-09).

## TEST BẮT BUỘC (JUnit 5)
- Tách đơn nhiều Teacher.
- Bán vượt tồn khi đồng thời (test song song bằng `ExecutorService` + `CountDownLatch`).
- Hết hạn đơn trả tồn đúng một lần (Idempotency).
- Mua trùng khóa cho cùng Child (SHOP-04).
- Child gọi API mua bị Spring Security chặn 403 Forbidden.

## KẾT QUẢ
Báo cáo cuối: danh sách endpoint `@RestController`, giả định, câu hỏi mở.
