# Hướng Dẫn Sử Dụng Swagger BE2 (Art Course & Supplies)

Tài liệu API được **tự sinh từ code** (springdoc-openapi) nên luôn khớp controller và DTO.

## 1. Xem tài liệu

Khi ứng dụng chạy:
- Giao diện Swagger UI: **`http://localhost:8080/swagger-ui.html`**
- Đặc tả OpenAPI dạng JSON: `http://localhost:8080/v3/api-docs`

Cả hai đường dẫn được `permitAll()` trong `SecurityConfig.java`.

Muốn import vào Postman/Insomnia: nhập đường dẫn `http://localhost:8080/v3/api-docs`.

Đổi code xong chỉ cần chạy lại ứng dụng là tài liệu cập nhật.

---

## 3. Xác Thực & Cấp Quyền (Authentication & Authorization)

Hệ thống sử dụng **JWT HS256** theo hợp đồng với BE1 ([`docs/contracts/jwt-contract.md`](../contracts/jwt-contract.md)).

### Cách cấu hình trong Swagger UI:
1. Bấm nút **Authorize** (biểu tượng chiếc khóa ở góc phải Swagger UI).
2. Nhập token vào ô `Value`:
   ```text
   eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   ```
3. Bấm **Authorize** -> **Close**. Swagger UI sẽ tự động gắn header `Authorization: Bearer <token>` vào mọi yêu cầu.

---

## 4. Danh Mục API Theo Phân Quyền & Vai Trò

### 1. Công khai (Public / Không cần token)
| Method | Endpoint | Mô tả | Mã SRS |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/shop/courses` | Danh sách khóa học đã duyệt (không lộ video) | SHOP-01 |
| `GET` | `/api/v1/shop/courses/{id}` | Chi tiết khóa học (chỉ metadata bài học) | SHOP-01 |
| `GET` | `/api/v1/shop/courses/lessons/{id}/trial` | Lấy Signed URL xem thử bài học (thời hạn 1h) | SHOP-10 |
| `GET` | `/api/v1/course-reviews/course/{id}` | Xem danh sách đánh giá khóa học | SHOP-09 |
| `GET` | `/api/v1/supplies` | Xem danh mục dụng cụ vẽ & tồn khả dụng | SHOP-02 |
| `GET` | `/api/v1/supplies/{id}` | Xem chi tiết dụng cụ vẽ | SHOP-02 |
| `POST` | `/api/v1/payments/webhook/{gateway}` | Webhook IPN xử lý kết quả thanh toán | PAY-01, PAY-02 |
| `GET` | `/api/v1/payments/webhook/{gateway}` | Webhook GET (VNPay callback) | PAY-09 |
| `GET` | `/api/v1/payments/return/{gateway}` | Return URL điều hướng người dùng | PAY-01 |

### 2. Phụ huynh (Role: `PARENT`)
| Method | Endpoint | Mô tả | Mã SRS |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/cart` | Lấy các mục trong giỏ hàng | SHOP-03 |
| `POST` | `/api/v1/cart/items` | Thêm khóa học/dụng cụ vào giỏ (chặn mua trùng) | SHOP-03, SHOP-04 |
| `PATCH`| `/api/v1/cart/items/{id}` | Cập nhật số lượng mục trong giỏ | SHOP-08 |
| `DELETE`| `/api/v1/cart/items/{id}` | Xóa mục khỏi giỏ hàng | SHOP-03 |
| `POST` | `/api/v1/orders/checkout` | Đặt hàng (tách đơn theo GV, giữ chỗ tồn) | SHOP-05, SHOP-06 |
| `GET` | `/api/v1/orders/my-orders` | Xem danh sách đơn hàng đã đặt | SHOP-05 |
| `POST` | `/api/v1/payments/create` | Tạo link thanh toán VNPay/MoMo/Mock | PAY-01 |
| `POST` | `/api/v1/course-reviews/course/{id}` | Gửi đánh giá khóa học kích hoạt escrow | SHOP-09 |
| `POST` | `/api/v1/fulfillment/parent/items/{id}/confirm-received` | Phụ huynh xác nhận đã nhận hàng | FUL-03 |
| `POST` | `/api/v1/fulfillment/parent/items/{id}/cancel` | Hủy dòng dụng cụ khi GV chưa xác nhận | FUL-06 |
| `POST` | `/api/v1/refunds/request` | Yêu cầu hoàn tiền theo dòng chi tiết | FIN-07 |
| `GET` | `/api/v1/refunds/my` | Xem các yêu cầu hoàn tiền của mình | FIN-07 |

### 3. Giáo viên (Role: `TEACHER`)
| Method | Endpoint | Mô tả | Mã SRS |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/orders/teacher-orders` | Danh sách đơn hàng bán được của GV | SHOP-05 |
| `POST` | `/api/v1/fulfillment/teacher/items/{id}/confirm-or-reject` | Xác nhận hoặc từ chối đơn dụng cụ | FUL-01 |
| `POST` | `/api/v1/fulfillment/teacher/items/{id}/shipping` | Nhập đơn vị VC & mã vận đơn | FUL-02 |
| `GET` | `/api/v1/teacher/finance/balance` | Tra cứu số dư sổ cái thời gian thực | FIN-01..03 |
| `POST` | `/api/v1/teacher/finance/withdrawals` | Gửi yêu cầu rút tiền (khóa số dư) | FIN-04, BR-15 |
| `GET` | `/api/v1/teacher/finance/withdrawals` | Lịch sử các yêu cầu rút tiền | FIN-04 |

### 4. Nhân viên & Quản trị viên (Role: `STAFF`, `ADMIN`)
| Method | Endpoint | Mô tả | Quyền |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/orders/{orderId}` | Xem chi tiết đơn hàng | PARENT, TEACHER, STAFF, ADMIN |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Hủy đơn chờ thanh toán | PARENT, STAFF, ADMIN |
| `GET` | `/api/v1/payments/{paymentId}/status` | Truy vấn trạng thái thanh toán | PARENT, TEACHER, STAFF, ADMIN |
| `POST` | `/api/v1/fulfillment/delivered/{id}` | Báo giao hàng tới nơi | TEACHER, STAFF, ADMIN |
| `POST` | `/api/v1/fulfillment/staff/items/{id}/handle-failure` | Xử lý giao thất bại (giao lại / hoàn tiền) | STAFF, ADMIN |
| `POST` | `/api/v1/staff/supplies/{id}/moderate` | Kiểm duyệt dụng cụ vi phạm | STAFF, ADMIN |
| `GET` | `/api/v1/staff/complaints` | Việc cần xử lý: hoàn tiền chờ duyệt (`REFUND_REQUEST`) và dòng dụng cụ giao thất bại (`DELIVERY_FAILED`), kèm tên phụ huynh/giáo viên | STAFF, ADMIN |
| `GET` | `/api/v1/staff/complaints/{orderId}/conversations` | Xem tin nhắn giải quyết khiếu nại (`messages`: chỉ chat customize thuộc đơn, giữa đúng phụ huynh và giáo viên) | STAFF, ADMIN (MSG-06) |
| `GET` | `/api/v1/admin/finance/withdrawals` | Danh sách yêu cầu rút tiền | STAFF, ADMIN |
| `POST` | `/api/v1/admin/finance/withdrawals/{id}/approve` | Duyệt yêu cầu rút tiền | ADMIN |
| `POST` | `/api/v1/admin/finance/withdrawals/{id}/reject` | Từ chối yêu cầu rút tiền | ADMIN |
| `POST` | `/api/v1/admin/finance/withdrawals/{id}/paid` | Xác nhận chi trả thủ công | STAFF, ADMIN |
| `GET` | `/api/v1/admin/finance/configs` | Xem danh sách cấu hình hệ thống | STAFF, ADMIN |
| `PUT` | `/api/v1/admin/finance/configs/{key}` | Cập nhật cấu hình hệ thống | ADMIN |
| `GET` | `/api/v1/refunds` | Danh sách hoàn tiền kèm `parentName` (`status`, `page` từ 0, `size` 1-200), mới nhất trước | STAFF, ADMIN |
| `POST` | `/api/v1/refunds/{id}/approve` | Phê duyệt yêu cầu hoàn tiền | STAFF, ADMIN |
| `POST` | `/api/v1/refunds/{id}/reject` | Từ chối yêu cầu hoàn tiền đang chờ duyệt (body `reason`) | STAFF, ADMIN |
| `POST` | `/api/v1/refunds/{id}/complete` | Xác nhận hoàn tất chuyển tiền hoàn | STAFF, ADMIN |
| `GET` | `/api/v1/admin/analytics/finance` | Thống kê phân tích tài chính toàn sàn | ADMIN |
| `GET` | `/api/v1/admin/analytics/ledger-audit` | Tra cứu sổ cái tài chính kiểm toán | ADMIN |
| `POST` | `/api/v1/moderation/reviews` | Ghi nhận nhật ký kiểm duyệt | STAFF, ADMIN |
| `GET` | `/api/v1/moderation/reviews/{type}/{id}` | Xem lịch sử kiểm duyệt theo đối tượng | STAFF, ADMIN |
| `GET` | `/api/v1/moderation/reviews/staff/{id}` | Xem lịch sử kiểm duyệt theo nhân viên | ADMIN |

### 5. Thông báo người dùng chung (Authenticated)
| Method | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/api/v1/notifications/my-notifications` | Lấy danh sách thông báo của tôi |
| `PATCH`| `/api/v1/notifications/{id}/read` | Đánh dấu 1 thông báo đã đọc |
| `PATCH`| `/api/v1/notifications/read-all` | Đánh dấu tất cả thông báo đã đọc |
