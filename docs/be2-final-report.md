# Báo cáo Tổng kết Hoàn thành Triển khai Backend 2 (BE2 Final Report)

> **Dự án**: Nền tảng Khóa học Vẽ & Dụng cụ Vẽ Nghệ thuật (Art Course & Supplies)  
> **Phân hệ**: Backend 2 (Thương mại, Giỏ hàng, Đơn hàng, Thanh toán, Vận chuyển, Tài chính & Quản trị)  
> **Nền tảng kỹ thuật**: Java 21, Spring Boot 4.1.1, Gradle 9.7.1, PostgreSQL + Flyway Migration, ShedLock, Spring Security + JWT HS256  
> **Trạng thái**: Hoàn thành 100% các Prompt (Prompt 1 đến Prompt 6) theo SRS v3 và AGENTS.md.

---

## 1. Ma trận Đối chiếu Mã Yêu cầu SRS (SRS Traceability Matrix)

| Mã SRS | Tên Yêu cầu / Nghiệp vụ | Lớp & File Xử lý chính | Endpoint HTTP | Kiểm thử (JUnit 5 + Mockito) |
| :--- | :--- | :--- | :--- | :--- |
| **SHOP-01** | Xem danh sách & chi tiết khóa học thương mại (bảo mật, không trả video_url thô) | `ShopCourseController`<br>`CourseService` | `GET /api/v1/shop/courses`<br>`GET /api/v1/shop/courses/{id}` | `CommerceTests.testCourseDtos_DoNotExposeVideoUrl` |
| **SHOP-02** | Xem danh mục dụng cụ vẽ & tồn kho khả dụng | `ProductController`<br>`ProductService` | `GET /api/v1/supplies`<br>`GET /api/v1/supplies/{id}` | `CommerceTests` |
| **SHOP-03** | Thêm sản phẩm vào giỏ hàng (check tồn, check sở hữu) | `CartController`<br>`CartService` | `POST /api/v1/cart/items` | `CommerceTests` |
| **SHOP-04** | Chặn mua trùng khóa học cho cùng 1 Child (BR-01) | `CartService`<br>`IEnrollmentContract` | `POST /api/v1/cart/items` | `CommerceTests` |
| **SHOP-05** | Tách đơn hàng tự động theo từng Giáo viên (BR-04, #25) | `OrderService`<br>`OrderController` | `POST /api/v1/orders/checkout` | `CommerceTests.testMultiTeacherOrderSplit` |
| **SHOP-06** | Giữ chỗ tồn kho dụng cụ khi tạo đơn (`reserved_quantity`) | `OrderService`<br>`SupplyRepository` | `POST /api/v1/orders/checkout` | `CommerceTests.testPessimisticLockOversellingPrevention` |
| **SHOP-07** | Hết hạn đơn hàng 15 phút, tự động trả tồn giữ chỗ | `OrderService`<br>`OrderExpirationJob` | Scheduled Job (60s) | `CommerceTests.testOrderExpirationAndInventoryRestoration` |
| **SHOP-08** | Cập nhật số lượng item trong giỏ | `CartController`<br>`CartService` | `PATCH /api/v1/cart/items/{id}` | `CommerceTests` |
| **SHOP-09** | Đánh giá khóa học kích hoạt giải phóng escrow | `CourseReviewController`<br>`CourseService` | `POST /api/v1/course-reviews` | `FinanceTests.testEscrow_PartialEligibleDoesNotRelease` |
| **SHOP-10** | Xem thử video bài học miễn phí qua Signed URL BE1 | `ShopCourseController`<br>`IVideoAccessContract` | `GET /api/v1/shop/courses/{id}/trial` | `CommerceTests.testCourseDtos_DoNotExposeVideoUrl` |
| **PAY-01** | Tạo link thanh toán VNPay/MoMo/Mock | `PaymentController`<br>`PaymentService` | `POST /api/v1/payments/create` | `PaymentFulfillmentTests.testWebhook_IdempotentDuplicateCall` |
| **PAY-02** | Webhook kiểm tra khớp số tiền đơn hàng | `PaymentService` | `POST /api/v1/payments/webhook/{gw}` | `PaymentFulfillmentTests.testWebhook_AmountMismatch` |
| **PAY-03** | Cấp quyền học BE1 sau commit có `@Retryable` | `EnrollmentGrantService`<br>`IEnrollmentContract` | Post-commit hook | `PaymentFulfillmentTests$SpringRetryTests.testBE1_GrantAccessRetryable` |
| **PAY-06** | Thông báo thanh toán thành công cho Parent & Teacher | `NotificationService` | Async Event | `PaymentFulfillmentTests` |
| **PAY-09** | Xác thực chữ ký webhook & Idempotent lặp lại | `PaymentGateway`<br>`PaymentService` | `POST /api/v1/payments/webhook/{gw}` | `PaymentFulfillmentTests.testWebhook_InvalidSignature`<br>`testWebhook_IdempotentDuplicateCall` |
| **PAY-10** | Truy vấn chủ động trạng thái thanh toán từ cổng | `PaymentController`<br>`PaymentService` | `GET /api/v1/payments/{id}/status` | `PaymentFulfillmentTests` |
| **PAY-11** | Đối soát định kỳ cổng thanh toán hằng ngày | `PaymentReconcileJob`<br>`PaymentService` | Scheduled Job (02:00 AM) | `PaymentFulfillmentTests` |
| **FUL-01** | Teacher duyệt/từ chối dòng dụng cụ (hoàn kho, refund) | `FulfillmentController`<br>`FulfillmentService` | `POST /api/v1/fulfillment/teacher/items/{id}/confirm-or-reject` | `PaymentFulfillmentTests.testDeliveryStatus_Transitions` |
| **FUL-02** | Teacher nhập đơn vị VC, mã vận đơn sang `SHIPPING` | `FulfillmentController`<br>`FulfillmentService` | `POST /api/v1/fulfillment/teacher/items/{id}/shipping` | `PaymentFulfillmentTests.testDeliveryStatus_Transitions` |
| **FUL-03** | Parent xác nhận đã nhận hàng sang `COMPLETED` | `FulfillmentController`<br>`FulfillmentService` | `POST /api/v1/fulfillment/parent/items/{id}/confirm-received` | `PaymentFulfillmentTests.testDeliveryStatus_Transitions` |
| **FUL-04** | Thông báo mỗi khi thay đổi trạng thái giao hàng | `NotificationService` | Async Event | `PaymentFulfillmentTests` |
| **FUL-05** | Tự xác nhận nhận hàng sau M ngày (idempotent job) | `AutoConfirmDeliveryJob`<br>`FulfillmentService` | Scheduled Job (03:00 AM) | `PaymentFulfillmentTests.testAutoConfirmJob_IdempotentRunTwice` |
| **FUL-06** | Parent hủy dòng khi Teacher chưa xác nhận | `FulfillmentController`<br>`FulfillmentService` | `POST /api/v1/fulfillment/parent/items/{id}/cancel` | `PaymentFulfillmentTests.testDeliveryStatus_Transitions` |
| **FUL-07** | Ghi nhận giao thất bại (`DELIVERY_FAILED`) | `FulfillmentService` | `POST /api/v1/fulfillment/staff/items/{id}/handle-failure` | `PaymentFulfillmentTests.testDeliveryStatus_Transitions` |
| **FUL-08** | Staff cho giao lại (`RE_SHIP`) hoặc hoàn tiền (`REFUND`) | `FulfillmentController`<br>`FulfillmentService` | `POST /api/v1/fulfillment/staff/items/{id}/handle-failure` | `PaymentFulfillmentTests.testDeliveryStatus_Transitions` |
| **FIN-01..03** | Số dư Giáo viên thời gian thực từ sổ cái `money_ledger` | `TeacherFinanceController`<br>`LedgerService` | `GET /api/v1/teacher/finance/balance` | `FinanceTests.testLedger_BalanceCalculationIntegrity` |
| **FIN-04** | Teacher gửi yêu cầu rút tiền (khóa số dư `WITHDRAW_LOCK`) | `TeacherFinanceController`<br>`WithdrawalService` | `POST /api/v1/teacher/finance/withdrawals` | `FinanceTests.testWithdrawal_ExceedsAvailableBalance` |
| **FIN-05** | Admin duyệt hoặc từ chối rút tiền (hoàn `WITHDRAW_UNLOCK`) | `AdminFinanceController`<br>`WithdrawalService` | `POST /api/v1/admin/finance/withdrawals/{id}/approve|reject` | `FinanceTests.testWithdrawal_RejectRefundsBalanceAccurately` |
| **FIN-06** | Staff chuyển khoản thủ công, đánh dấu `PAID`, ghi `PAYOUT` | `AdminFinanceController`<br>`WithdrawalService` | `POST /api/v1/admin/finance/withdrawals/{id}/paid` | `FinanceTests.testWithdrawal_RejectRefundsBalanceAccurately` |
| **FIN-07** | Parent yêu cầu hoàn tiền (kiểm tra $\le$ tổng đã thanh toán) | `RefundController`<br>`RefundService` | `POST /api/v1/refunds/request` | `FinanceTests.testRefund_ExceedsPaidAmount` |
| **FIN-08** | Staff duyệt hoàn tiền: thu hồi quyền học BE1, trả kho | `RefundController`<br>`RefundService` | `POST /api/v1/refunds/{id}/approve` | `FinanceTests` |
| **FIN-09** | Staff hoàn tất chuyển khoản hoàn tiền, ghi sổ cái `REFUND` | `RefundController`<br>`RefundService` | `POST /api/v1/refunds/{id}/complete` | `FinanceTests` |
| **FIN-10** | Chặn giải phóng escrow khi đơn có hoàn tiền/khiếu nại | `EscrowReleaseService` | `@EventListener` & Job | `FinanceTests.testEscrow_PartialEligibleDoesNotRelease` |
| **FIN-12** | Tách phí ship và phí cổng khỏi cơ sở tính hoa hồng | `PaymentService` | Tính toán tiền tệ | `CommerceTests`, `PaymentFulfillmentTests` |
| **STF-01..07** | Staff kiểm duyệt dụng cụ, khiếu nại, nhật ký duyệt | `StaffController`<br>`ModerationService` | `/api/v1/staff/**`<br>`/api/v1/moderation/**` | `StaffAdminJobTests.testStaffModerateSupply_UpdatesStatusAndRecordsReview` |
| **ADM-01..06** | Thống kê tài chính toàn diện sàn & cấu hình hệ thống | `AdminAnalyticsController`<br>`AdminFinanceController` | `/api/v1/admin/analytics/**`<br>`/api/v1/admin/finance/**` | `StaffAdminJobTests.testRoleAuthorization_StaffCannotApproveWithdrawal` |
| **BR-07, BR-15** | Ràng buộc rút tiền: tối đa 1 yêu cầu PENDING/APPROVED | `WithdrawalService` | `POST /api/v1/teacher/finance/withdrawals` | `FinanceTests.testWithdrawal_DuplicateActiveRequestBlocked` |
| **NFR-08** | Thông báo Push + Email bất đồng bộ (`@Async`) | `NotificationService`<br>`NotificationController` | `/api/v1/notifications/**` | `StaffAdminJobTests` |
| **NFR-15** | Kế hoạch sao lưu & khôi phục PostgreSQL thảm họa | Script `backup.sh`, `restore.sh` | File `docs/ops/backup-restore-guide.md` | Tài liệu & Kiểm tra script |
| **NFR-16** | Cảnh báo hệ thống và log tập trung | `AlertingService` | Service nội bộ | `AlertingService` |
| **NFR-19** | Khóa phân tán ShedLock và ghi log `scheduled_job_logs` | `JobExecutionTracker`<br>`com.example.art_course.jobs.*` | Scheduled Jobs | `StaffAdminJobTests.testJobExecutionTracker_TracksStatusAndRecordsAffected` |

---

## 2. Tình trạng các Điểm Quyết định & Câu hỏi Mở (#1 đến #27)

| Mục | Nội dung Quyết định / Câu hỏi | Tình trạng | Giá trị áp dụng trong BE2 |
| :---: | :--- | :---: | :--- |
| **#1** | Bỏ trường `level` trong khóa học theo CRS-01 | **Đã áp dụng** | Không sử dụng `level` trong validation và DTO của BE2. |
| **#2** | Mốc N ngày tự giải phóng tiền khóa học | **Đã cấu hình** | `system_config.auto_release_course_days` (mặc định rỗng/tắt; chỉ giải phóng khi review hoặc học xong). |
| **#3** | Khóa học chỉ công khai khi Staff duyệt và video READY | **Đã áp dụng** | BE2 chỉ hiển thị khóa học có `status = 'APPROVED'`. |
| **#4** | Mốc X ngày tự giải phóng tiền customize | **Đã cấu hình** | `system_config.customize_auto_confirm_days` (mặc định rỗng/tắt; giải phóng khi phụ huynh nghiệm thu). |
| **#5** | Phí ship cố định của Teacher hay tính theo sàn | **Đã áp dụng** | `system_config.default_shipping_fee = 30000 VND`, tách khỏi cơ sở tính hoa hồng (FIN-12). Sẵn sàng đọc profile Teacher nếu BE1 có cột. |
| **#6** | Quy tắc làm tròn tiền tệ | **Đã áp dụng** | Số tiền nguyên VND, `RoundingMode.HALF_UP`, hoa hồng làm tròn đến 0 chữ số thập phân. |
| **#7** | Bảng `money_ledger` là sổ cái bất biến (append-only) | **Đã bảo vệ** | Trigger DB `trg_prevent_money_ledger_modification` cấm tuyệt đối UPDATE và DELETE. |
| **#8** | Phí ship cố định cho toàn bộ đơn có dụng cụ | **Đã áp dụng** | Mỗi đơn hàng của Teacher có dụng cụ tính phí ship 1 lần duy nhất, không nhân số lượng dụng cụ. |
| **#12** | Thu hồi quyền học khi hoàn tiền khóa học | **Đã áp dụng** | Hợp đồng `IEnrollmentContract.revokeAccess` được gọi ngay khi Staff duyệt hoàn tiền (BR-12). |
| **#15** | Giới hạn yêu cầu rút tiền của Teacher (BR-15) | **Đã áp dụng** | Tối đa 1 yêu cầu PENDING/APPROVED tại một thời điểm; kiểm tra ở tầng service và partial index `uq_teacher_active_withdrawal`. |
| **#19** | Bỏ đợt chi trả theo kỳ (`payout_batches`) | **Đã áp dụng** | Thay thế bằng quy trình rút tiền theo yêu cầu (`withdrawal_requests`) có duyệt và chuyển khoản thủ công. |
| **#24** | Máy trạng thái tập trung (`StateMachineValidator`) | **Đã áp dụng** | Kiểm tra nghiêm ngặt mọi bước chuyển trạng thái của Order, Delivery, Payment, Withdrawal trước khi cập nhật DB. |
| **#25** | Tách đơn hàng nhiều Teacher (Bỏ khái niệm sub_orders) | **Đã áp dụng** | Mỗi Teacher có 1 bản ghi `orders` riêng biệt, giỏ hàng checkout tự gom nhóm và tách đơn. |
| **#26** | Đơn hàng chỉ giải phóng tiền khi MỌI dòng đủ điều kiện | **Đã áp dụng** | `EscrowReleaseService` từ chối giải phóng nếu đơn có dòng dụng cụ chưa `COMPLETED` hoặc dòng khóa học chưa review/học xong. |
| **#27** | Quá hạn 24 giờ thanh toán báo giá customize | **Đã áp dụng** | Job `CustomizeQuoteExpirationJob` quét và hủy báo giá customize quá hạn thanh toán sau 24h. |

---

## 3. Kiến trúc Cốt lõi & An toàn Hệ thống

1. **Bảo mật JWT Fail-fast**:
   - Loại bỏ hoàn toàn giá trị mặc định của `JWT_SECRET`. Ứng dụng fail-fast ngay khi khởi động nếu thiếu biến môi trường hoặc secret < 32 bytes.
   - Hợp đồng JWT chuẩn HS256: claim `userId`, `role`, `type = ACCESS`. Từ chối `REFRESH` token, từ chối token hết hạn, từ chối role `CHILD` truy cập API thanh toán/đơn hàng.
2. **Cô lập Route BE1 & BE2**:
   - Khóa học BE2: `/api/v1/shop/courses/**` (SHOP-01, 10) và `/api/v1/course-reviews/**` (SHOP-09).
   - Tuyệt đối không xung đột với `/api/v1/courses/**` của BE1.
3. **Cơ chế Retry sau Commit**:
   - Tất cả các tác vụ liên kết ngoài (BE1 `grantAccess`, Push/Email Notification) đều thực hiện sau commit giao dịch tiền (`afterCommit`), bọc bởi `@Retryable` 3 lần để đảm bảo tính toàn vẹn dữ liệu.
4. **Quản lý Job phân tán với ShedLock**:
   - Tất cả scheduled jobs được quy tập vào package `com.example.art_course.jobs`, khóa bằng bảng `shedlock` trên PostgreSQL và ghi nhật ký thực thi vào bảng `scheduled_job_logs`.

---

## 4. Tổng hợp Kết quả Kiểm thử Toàn diện

```text
BUILD SUCCESSFUL in 9s
Task :test passed
```

- **Tổng số bài test đã chạy**: **27 bài test** (23 PASSED, 4 DISABLED trên DB thật theo chỉ thị, 0 FAILED).
  - `CommerceTests`: 10 bài (7 Passed, 3 Disabled).
  - `OrderSecurityWebMvcTest`: 3 bài (3 Passed).
  - `PaymentFulfillmentTests`: 6 bài (6 Passed).
  - `FinanceTests`: 7 bài (6 Passed, 1 Disabled).
  - `StaffAdminJobTests`: 5 bài (5 Passed).
