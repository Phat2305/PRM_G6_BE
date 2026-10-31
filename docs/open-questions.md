# Danh sách câu hỏi mở & Vấn đề cần chốt (BE2)

Nguồn sự thật ưu tiên:
1. SRS bản thảo lần 3
2. ERD đã sửa (file erd.drawio.svg)
3. ART_COUSE.sql (bản cũ, tham khảo hiện trạng)

---

## 1. Mâu thuẫn giữa ERD sơ khai và SRS bản thảo lần 3
- **Đơn con (sub_orders)**: 
  - *ERD cũ (trang 1 mxfile)*: Có bảng `sub_orders`, `sub_order_supplies`, `order_courses`.
  - *SRS bản thảo lần 3 (trang 1, SHOP-05, BR-04, #25)*: ĐÃ BỎ KHÁI NIỆM ĐƠN CON. Mỗi giỏ hàng có nhiều Teacher được tách thành mỗi Teacher một đơn (`orders` có `teacher_id` và `parent_id`). Dụng cụ và khóa học nằm chung ở `order_details`. Phí ship nằm ở `orders`.
  - *Giải pháp BE2*: Tuân thủ SRS bản thảo lần 3 (nguồn ưu tiên 1). Một bảng `orders` (thuộc đúng 1 Teacher) và một bảng `order_details` (mỗi dòng là khóa học hoặc dụng cụ).

- **Khoản tiền giữ (held_funds) & Đợt chi trả (payout_batches)**:
  - *ERD cũ*: Dùng `held_funds` và `payout_batches`.
  - *SRS bản thảo lần 3 (trang 1, PAY-05, FIN-01..06, #19)*: ĐÃ BỎ đợt chi trả theo kỳ. Dùng `payments` (trạng thái PENDING, HELD, RELEASED, FAILED, REFUNDED) gắn trực tiếp với `orders`. Giáo viên rút tiền theo yêu cầu qua `withdrawal_requests`.
  - *Giải pháp BE2*: Dùng `payments` và `withdrawal_requests` theo SRS bản thảo lần 3.

---

## 2. Các tham số cấu hình chưa chốt từ SRS mục 10
- **Mục 10, #2 (Mốc N ngày tự giải phóng tiền khóa học)**: Chưa chốt số ngày. Tạm để trong `system_config` key `auto_release_course_days`, mặc định là `null` (tắt). Tiền khóa học chỉ giải phóng khi học xong hoặc phụ huynh đánh giá (mục 7.1).
- **Mục 10, #4 (Mốc X ngày khóa customize)**: Chưa chốt số ngày. Tạm để trong `system_config` key `customize_auto_confirm_days`, mặc định là `null` (tắt).
- **Mục 10, #3 (Quy tắc hoàn tiền và tranh chấp)**: Chưa có bộ quy tắc chi tiết; dựng khung hoàn tiền thủ công do Staff xử lý từng ca (`refunds`, `refund_items`).

---

## 3. Quy chuẩn làm tròn tiền tệ
- Đơn vị tiền tệ: VND (không có phần thập phân / cents).
- Tỷ lệ hoa hồng cấu hình dưới dạng số thập phân (ví dụ 0.20 cho 20%).
- Tiền hoa hồng = Round(Tổng tiền sản phẩm * Tỷ lệ hoa hồng, 0, ROUND_HALF_UP).
- Tiền thực nhận của Teacher = Tổng tiền sản phẩm - Tiền hoa hồng + Phí ship (phí ship 0% hoa hồng).

---

## 4. Các điểm ERD & Thiết kế cần phê duyệt từ Audit (Prompt 1)

### (a) Vị trí lưu Child được gán khóa học khi tạo đơn
- **Lựa chọn A**: Lưu `child_id` trực tiếp tại bảng `orders_detail` (nullable, bắt buộc khi dòng là khóa học).
- **Lựa chọn B**: Duy trì bảng trung gian `children_course` như schema cũ.
- **Đề xuất BE2**: Chọn **Lựa chọn A** để tránh dư thừa bảng trung gian, tối ưu hiệu năng truy vấn lịch sử đơn hàng của phụ huynh. Bỏ bảng `children_course`.

### (b) Liên kết đơn hàng customize khi khóa học chưa được tạo (#27)
- **Vấn đề**: Khi Parent đồng ý báo giá, đơn hàng phải được thanh toán trước, nhưng Teacher chưa tạo khóa học.
- **Đề xuất BE2**: Bổ sung `customize_request_id` (hoặc `offer_id`) vào `orders_detail`. Khi thanh toán thành công, cập nhật yêu cầu customize sang `IN_PROGRESS`. Khi Teacher hoàn thành và đăng tải khóa học, gắn `courses.customize_request_id` vào request để kích hoạt giải phóng tiền escrow.

### (c) Cách quản lý tồn kho giữ chỗ và đã bán của dụng cụ vẽ
- **Lựa chọn A**: Thêm 2 cột `reserved_quantity` và `sold_quantity` vào bảng `supplies`.
- **Lựa chọn B**: Tạo bảng nhật ký tồn kho riêng biệt.
- **Đề xuất BE2**: Chọn **Lựa chọn A** để đảm bảo tốc độ truy vấn tồn khả dụng (`stock_quantity - reserved_quantity`) và dễ dàng thực hiện update có điều kiện chống bán vượt tồn.

### (d) Nơi lưu trữ giỏ hàng (Cart)
- **Lựa chọn A**: Lưu trên Server (`carts`, `cart_items`).
- **Lựa chọn B**: Lưu tại Client/App.
- **Đề xuất BE2**: Chọn **Lựa chọn A** để đồng bộ giỏ hàng đa thiết bị và cho phép Backend kiểm tra logic ràng buộc (chặn mua trùng khóa cho cùng 1 Child, check tồn kho thời gian thực trước khi checkout).

### (e) Môi trường Database Dev (`DATABASE_URL_DEV`)
- **Hiện trạng**: Chưa có file `.env.local` hoặc `DATABASE_URL_DEV`.
- **Đề xuất BE2**: Cần khởi tạo cấu hình database dev cục bộ (PostgreSQL Local / Docker) trước khi thực hiện Prompt 2 (Migration). Tuyệt đối không chạy trên database Supabase thật.

### (f) Công cụ Database Migration
- **Đề xuất BE2**: Dùng **Flyway** (`org.flywaydb:flyway-core` và `flyway-database-postgresql`) tích hợp trong Gradle để quản lý migration theo version SQL (`V1__...`, `V2__...`).

### (g) Thống nhất Stack công nghệ
- **Vấn đề**: Ghi chú tại `AGENTS.md` ghi Node.js/TypeScript, nhưng toàn bộ repo hiện tại là Java 21 + Spring Boot (Gradle).
- **Đề xuất BE2**: Tiếp tục hoàn thiện trên nền tảng **Java 21 + Spring Boot** hiện hữu của repository.

---

## 5. Cấu hình phí vận chuyển (Shipping Fee) và Phí theo Teacher
- **Vấn đề**: SRS quy định phí ship nằm ở đơn hàng, giáo viên đặt phí cố định nếu đơn có dụng cụ (#8). Hiện tại BE2 trước mắt đang hỗ trợ đọc từ `system_config` (khóa `shipping_fee_default`, mặc định 30.000 VND).
- **Câu hỏi mở cho BE1 / Database Schema**:
  1. Bảng `teachers` hoặc `teacher_profiles` do BE1 quản lý đã có cột `shipping_fee` chưa?
  2. Nếu có, BE2 sẽ ưu tiên đọc phí ship riêng của từng Teacher (`teacher_profiles.shipping_fee`), nếu null thì fallback về `system_config` khóa `shipping_fee_default`.
  3. Nếu đơn hàng gồm nhiều dụng cụ của cùng một Teacher thì phí ship là cố định cho cả đơn (không nhân số lượng dụng cụ), hay tính theo trọng lượng? SRS #8 chốt: **Teacher đặt phí cố định cho toàn bộ đơn có dụng cụ**.
- **Hiện trạng xử lý tại BE2**: `OrderService.resolveShippingFee(teacherId)` gọi `ConfigService.getDefaultShippingFee()` lấy từ bảng `system_config` (khóa `shipping_fee_default`), sẵn sàng mở rộng tra cứu thêm cột `shipping_fee` từ profile của Teacher khi BE1 cung cấp.

---

## 6. Thiết kế & Giả định Thanh toán và Giao hàng (Prompt 4)
- **Tách cơ sở tính hoa hồng (FIN-12)**:
  - Phí vận chuyển (shipping_fee) và phí cổng thanh toán (gateway_fee) được tách hoàn toàn khỏi cơ sở tính hoa hồng.
  - Hoa hồng chỉ tính trên `sub_total` của từng dòng (khóa học 20%, dụng cụ 10%).
  - Tiền thực nhận của Teacher = Tổng net_amount các dòng sản phẩm + Toàn bộ phí ship.
- **Tính toán tiền tệ và làm tròn**:
  - Dùng `BigDecimal` với scale = 0 và `RoundingMode.HALF_UP` cho toàn bộ số tiền VND.
- **Webhook Idempotency & Giao dịch an toàn**:
  - Idempotent dựa trên `(gateway, gateway_transaction_id)` hoặc `payment.status` (`HELD` / `RELEASED`). Webhook lặp được phản hồi `SUCCESS` kèm log `IGNORED` mà không chạy lại logic cộng trừ hay ghi sổ cái.
  - Toàn bộ bước cập nhật đơn sang `PAID`, payment sang `HELD`, trừ `reserved_quantity`, cộng `sold_quantity`, ghi `money_ledger` loại `HOLD` diễn ra trong một `@Transactional` duy nhất.
- **Cấp quyền học BE1 (PAY-03) & Thử lại (@Retryable)**:
  - Đóng gói trong `EnrollmentGrantService` kích hoạt sau commit giao dịch (`TransactionSynchronizationManager.registerSynchronization(afterCommit)`).
  - Tự động retry tối đa 3 lần với backoff lũy tiến khi BE1 gặp lỗi mạng hoặc quá tải.
- **Quy trình Giao hàng & Tự xác nhận (FUL-01..08)**:
  - Máy trạng thái `DeliveryStatus`: `PENDING` -> `CONFIRMED` -> `SHIPPING` -> `DELIVERED` -> `COMPLETED`.
  - Hủy dòng hoặc từ chối dòng: trả lại tồn kho khả dụng cho `supplies` (`stock_quantity` tăng, `sold_quantity` giảm) và tự động tạo `Refund` + `RefundItem` ở trạng thái `PENDING`.
  - Cron job tự động xác nhận (`autoConfirmDeliveredItemsJob`): quét các dòng `DELIVERED` quá M ngày (mặc định 7 ngày từ `system_config`), chạy an toàn phân tán qua ShedLock, đảm bảo idempotent.

---

## 7. Thiết kế & Giả định Tài chính, Escrow, Rút tiền & Hoàn tiền (Prompt 5)
- **Công thức tính Số dư Giáo viên (FIN-03)**:
  - *Số dư đang giữ (Held / Escrow)* = Tổng `net_amount` của các `payments` có `status == HELD` thuộc Teacher.
  - *Số dư khả dụng (Available Balance)* tính toán trực tiếp từ `money_ledger`:
    $$\text{Available} = \sum (\text{RELEASE}) + \sum (\text{WITHDRAW\_UNLOCK}) - \sum (\text{WITHDRAW\_LOCK}) - \sum (\text{REFUND})$$
  - *Số dư đang chờ rút (Pending Withdrawal)* = Tổng `total_amount` của các `withdrawal_requests` có `status IN ('PENDING', 'APPROVED')` của Teacher.
  - *Số dư đã rút (Withdrawn)* = Tổng `total_amount` của các `withdrawal_requests` có `status == 'PAID'` (hoặc tổng bút toán `PAYOUT` trong sổ cái).
  - Tuyệt đối không lưu cột số dư độc lập tại bảng `teachers` để tránh sai lệch và race condition.
- **Quy tắc Giải phóng Tiền ký quỹ (Escrow Release - Mục 7.1 & #26)**:
  - Một khoản thanh toán chỉ chuyển từ `HELD` sang `RELEASED` khi **MỌI** dòng chi tiết trong đơn hàng đều thỏa mãn điều kiện giải phóng. Nếu chỉ 1 phần dòng hoàn tất, toàn bộ khoản thanh toán vẫn ở trạng thái `HELD`.
  - Khóa học: Phụ huynh đánh giá (`CourseReviewedEvent`) HOẶC trẻ hoàn thành khóa học (`CourseCompletedEvent`) HOẶC mốc N ngày tự động nếu Admin kích hoạt (`auto_release_course_days`).
  - Dụng cụ: Phụ huynh xác nhận đã nhận hoặc mốc M ngày tự động xác nhận (`auto_confirm_delivery_days` = 7 ngày) -> `COMPLETED`.
  - Ràng buộc chặn giải phóng: Khi đơn hàng đang có yêu cầu hoàn tiền `PENDING` hoặc `APPROVED`, việc giải phóng bị tạm dừng (FIN-10).
- **Rút tiền & Ràng buộc BR-15**:
  - Tại một thời điểm, mỗi Giáo viên chỉ được có tối đa 1 yêu cầu rút tiền ở trạng thái `PENDING` hoặc `APPROVED`.
  - Khi tạo yêu cầu: Kiểm tra `amount <= available_balance`, tạo `WithdrawalRequest(PENDING)` và ghi sổ cái `WITHDRAW_LOCK`.
  - Khi Admin duyệt: Chuyển sang `APPROVED` (chờ Staff chi trả).
  - Khi Admin từ chối: Chuyển sang `REJECTED` kèm lý do, ghi sổ cái `WITHDRAW_UNLOCK` trả lại số dư.
  - Khi Staff chi trả thủ công: Đánh dấu `PAID` kèm mã giao dịch ngân hàng và ảnh ủy nhiệm chi, ghi sổ cái `PAYOUT`.
- **Hoàn tiền & Thu hồi quyền học (FIN-07..09, BR-12)**:
  - Phụ huynh gửi yêu cầu hoàn tiền cho từng dòng chi tiết; tổng hoàn không được vượt quá số tiền đã thanh toán của đơn.
  - Khi duyệt hoàn tiền khóa học: Gọi `IEnrollmentContract.revokeAccess` để thu hồi quyền học của trẻ (BR-12).
  - Khi duyệt hoàn tiền dụng cụ: Trả lại tồn kho khả dụng cho giáo viên nếu hàng chưa giao thành công.
  - Khi Staff hoàn tất chuyển khoản hoàn: Ghi sổ cái `REFUND` để trừ số dư tương ứng.

## 8. Đăng ký: phone và email

`RegisterRequest` bắt buộc `phone` và `email`, nhưng bảng `users`, `parent_profiles`, `teacher_profiles` không có hai cột này. `AuthService.register` chỉ lưu username, mật khẩu, role, status và `full_name` (giáo viên thêm `bank_account_info` rỗng). Frontend vẫn gửi phone và email để qua validation. Chưa thêm cột vì bảng thuộc BE1.




## 9. Mua trùng khóa học: chặn theo phụ huynh (SHOP-04)

SRS SHOP-04 ghi "cùng một bé", nhưng phụ huynh được phép thanh toán mà chưa gán bé và khóa customize có `order_details.course_id` null, nên chặn theo bé (`child_enroll`) bị hở. Chọn phương án tối thiểu: một khóa chỉ mua một lần cho mỗi phụ huynh; bé khác gán thêm ở đơn cũ bằng `assignChild` (giá 0, đã có sẵn).

- "Đã sở hữu" = có dòng `order_details` thuộc đơn `PAID` của phụ huynh, chưa `REFUNDED`, khớp `course_id` hoặc `customize_request_id` của khóa (`OrderDetailRepository.parentOwnsCourse`).
- Chặn ở giỏ (`CartService`), checkout (`OrderService`) và tạo link thanh toán (`PaymentService`); giỏ/lượt đặt không có hai dòng cùng khóa.
- Câu hỏi mở: nhóm có muốn cho mua lần hai có trả tiền cho bé khác không? Nếu có thì phải bỏ chặn theo phụ huynh và bắt gán bé lúc thanh toán.
