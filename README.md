# ART_COURSE — Backend (PRM_G6_BE)

Backend cho **Nền tảng khóa học vẽ và dụng cụ vẽ**: Teacher bán khóa học video và dụng cụ vẽ, Parent mua cho Child.
Hai nhóm backend dùng chung một codebase và một database:

- **BE1:** người dùng, hồ sơ, khóa học, bài học, tin nhắn, bài nộp, yêu cầu tùy biến.
- **BE2:** giỏ hàng, đơn hàng, thanh toán, hoàn tiền, rút tiền, sổ cái tiền, kiểm duyệt, thông báo.

## Công nghệ

Java 21 · Spring Boot 4.1.1 (Gradle) · Spring Security + JWT (HS256) · Spring Data JPA · PostgreSQL · Flyway · ShedLock · springdoc (Swagger) · Testcontainers.

## Yêu cầu

- JDK 21
- PostgreSQL local, hoặc Docker (script dev dùng container `pg-artcourse`)
- Docker để chạy test (Testcontainers)
- `ffmpeg` trong PATH nếu dùng tính năng upload/HLS video

## Cấu hình

Ứng dụng tự nạp file `.env` ở thư mục gốc khi khởi động. Biến môi trường hệ thống (nếu có) thắng giá trị trong file. Repo không có `.env.example`, hãy tự tạo `.env` với các biến sau:

| Biến | Ý nghĩa |
|------|---------|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Database mặc định (mặc định `localhost:5432/ART_COURSE`) |
| `DATABASE_URL_DEV` | Database dev riêng, dùng với profile `dev` |
| `JWT_SECRET` | Khóa ký JWT, tối thiểu 32 ký tự |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Gửi mail mã quên mật khẩu. Gmail dùng App Password |
| `SENDGRID_API_KEY` / `SENDGRID_FROM` | Gửi mail qua SendGrid (HTTPS). Có khóa này thì bỏ qua SMTP; cần khi deploy Render vì Render chặn cổng SMTP. `SENDGRID_FROM` phải là Single Sender (hoặc domain) đã xác minh trên SendGrid, nếu không SendGrid từ chối |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_FROM` | Tùy chọn (mặc định `smtp.gmail.com`, `587`, `MAIL_USERNAME`) |
| `PAYOS_CLIENT_ID` / `PAYOS_API_KEY` / `PAYOS_CHECKSUM_KEY` | Khóa kênh thanh toán PayOS (my.payos.vn). Thiếu thì tạo link thanh toán trả lỗi `PAYOS_NOT_CONFIGURED` |
| `PAYOS_ENDPOINT` | Tùy chọn, mặc định `https://api-merchant.payos.vn` |
| `SUPABASE_JDBC_URL` / `SUPABASE_DB_USERNAME` / `SUPABASE_DB_PASSWORD` | Chỉ cho profile `supabase` |
| `PORT` | Cổng chạy, mặc định 8080 |

> Không commit `.env` hay thông tin đăng nhập thật.

## Chạy ứng dụng

```powershell
# Mặc định: database local, Flyway tắt
.\gradlew.bat bootRun

# Profile dev: bật Flyway, dùng DATABASE_URL_DEV
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"

# Profile supabase: chỉ chạy runtime, không migrate
.\gradlew.bat bootRun --args="--spring.profiles.active=supabase"
```

Gradle dừng ở `80% EXECUTING` là bình thường, ứng dụng đang chạy. Dừng bằng `Ctrl+C`.

Swagger UI: <http://localhost:8080/swagger-ui.html> · OpenAPI JSON: <http://localhost:8080/v3/api-docs>

## Database và migration

- Schema nền: `docs/ART_COUSE.sql`. Migration BE2: `src/main/resources/db/migration/`:

  | File | Nội dung |
  |------|----------|
  | `V1__be2_schema_init.sql` | Schema BE2 |
  | `V2__ledger_hardening.sql` | Sổ cái chỉ-ghi-thêm |
  | `V3__payos_gateway.sql` | Cột `payments.gateway_order_code` (orderCode của PayOS), ràng buộc cổng chỉ `PAYOS` và `MOCK` (chỉ áp cho dòng mới) |
  | `V4__course_child_optional.sql` | Khóa học được phép chưa gán bé khi đặt hàng |
  | `V5__drop_order_detail_product_type.sql` | Bỏ cột `order_details.product_type`; loại dòng suy ra từ `course_id` / `supply_id` / `customize_request_id` |

  Chạy theo thứ tự V3 → V4 → V5. Profile `dev` tự áp bằng Flyway; Supabase phải chạy tay (SQL Editor).
- Flyway chỉ bật ở profile `dev`. Hibernate `ddl-auto` là `none` hoặc `validate`.
- **Không** chạy app, test hay migration vào database Supabase. Muốn áp migration lên Supabase, xuất file SQL rồi chạy thủ công.
- Script dev (PowerShell). Chỉ cho phép database `art_course_dev` trên localhost và cần biến `DEV_DB_PASSWORD`:
  `scripts/reset-dev-db.ps1`, `scripts/dev-up.ps1`, `scripts/dev-seed.ps1`, `scripts/dev-tokens.ps1`.

## Test

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests "com.example.art_course.modules.payment.PaymentFulfillmentTests"
.\gradlew.bat test --tests "*PayOSPaymentGatewayTest"   # không cần Docker
```

Các test dùng PostgreSQL thật (Testcontainers) cần Docker; không có Docker thì chúng báo lỗi, các test còn lại vẫn chạy.

## Xác thực

| Endpoint | Việc |
|----------|------|
| `POST /auth/register` | Đăng ký. `PARENT` vào trạng thái `ACTIVE`, `TEACHER` vào `PENDING` chờ duyệt |
| `POST /auth/login` | Trả JWT (`token`, `id`, `username`, `role`, `status`). Chỉ tài khoản `ACTIVE` đăng nhập được |
| `POST /auth/forgot-password` | Gửi mã xác nhận tới email |
| `POST /auth/reset-password` | Đặt mật khẩu mới bằng email + mã |

- Gửi token qua header `Authorization: Bearer <token>`. Token sống 24 giờ.
- Vai trò: `PARENT`, `CHILD`, `TEACHER`, `STAFF`, `ADMIN`. `CHILD` bị chặn ở các API đơn hàng, thanh toán, hoàn tiền, giao hàng và tài chính.
- Request mẫu: `docs/api/requests.http`.

## Mua hàng và thanh toán PayOS

Tất cả endpoint dưới đây (trừ webhook, danh mục và trang kết quả) cần JWT của `PARENT`.

| Endpoint | Việc |
|----------|------|
| `GET /api/v1/shop/courses`, `GET /api/v1/shop/courses/{id}` | Danh mục khóa học (công khai). Có `teacherName` = `teacher_profiles.full_name` |
| `GET /api/v1/supplies`, `GET /api/v1/supplies/{id}` | Dụng cụ vẽ (công khai). Có `teacherName` |
| `GET/POST /api/children`, `DELETE /api/children/{id}` | Quản lý tài khoản con (BE1) |
| `POST /api/v1/orders/checkout` | Tạo đơn, tự tách mỗi Teacher một đơn. Gửi `useCart:false` và `items[]` (`itemType`, `courseId` hoặc `supplyId`, `quantity`, `childId` tùy chọn cho khóa học) |
| `GET /api/v1/orders/my-orders`, `GET /api/v1/orders/{id}` | Danh sách / chi tiết đơn. Có `teacherName` |
| `POST /api/v1/orders/{orderId}/details/{detailId}/assign-child` | Chọn bé cho khóa học đã mua mà chưa gán. Body `{"childId": "..."}`. Cấp quyền học ngay |
| `POST /api/v1/orders/{id}/cancel` | Hủy đơn chưa thanh toán, trả tồn kho |
| `POST /api/v1/payments/create` | Body `{orderId, gateway:"PAYOS", returnUrl}`. Trả `paymentId` và `paymentUrl` (trang PayOS) |
| `GET /api/v1/payments/{paymentId}/status` | Trạng thái giao dịch; nếu còn `PENDING` thì hỏi lại PayOS |
| `POST /api/v1/payments/webhook/PAYOS` | Webhook PayOS (công khai, kiểm chữ ký HMAC-SHA256 bằng `PAYOS_CHECKSUM_KEY`) |

Luồng thanh toán:

1. Client gọi `payments/create`, mở `paymentUrl` ở trình duyệt, rồi hỏi `payments/{id}/status` mỗi vài giây.
2. PayOS gọi webhook khi khách trả tiền. Hệ thống kiểm chữ ký và số tiền, rồi chuyển đơn sang `PAID`, giữ tiền (`HELD`) và cấp quyền học cho các khóa đã có bé.
3. Sau thanh toán PayOS chuyển khách về `returnUrl` (cả khi thành công lẫn hủy) kèm `status` và `cancel`. Backend có sẵn trang tĩnh `/payment-result.html` cho việc này.

Cấu hình PayOS:

- Dán URL webhook vào kênh thanh toán trên PayOS: `https://prm-g6-be.onrender.com/api/v1/payments/webhook/PAYOS` (hoặc URL ngrok khi chạy local). Khi lưu, PayOS gửi một webhook mẫu; backend trả 200.
- Hiện chỉ nhận cổng `PAYOS`. Lớp VNPay/MoMo/Mock vẫn còn trong code, nhưng API tạo thanh toán từ chối cổng khác.
- Mỗi lần tạo link dùng một `orderCode` mới (lưu ở `payments.gateway_order_code`), nên đơn chưa trả có thể thanh toán lại.

## API backend và trạng thái nối với FE

Danh sách đầy đủ xem Swagger. Bảng dưới cho biết nhóm nào app Flutter (`PRM393_G6_FE`) đã gọi.

| Nhóm | Prefix | FE |
|------|--------|----|
| Xác thực | `/auth` (login, register, forgot-password, reset-password) | Đã nối |
| Danh mục công khai | `/api/v1/shop/courses`, `/api/v1/supplies` | Đã nối (danh sách, chi tiết khóa học) |
| Con | `/api/children` (GET, POST, DELETE) | Đã nối; `GET/PUT /{userId}` chưa dùng |
| Đơn hàng | `/api/v1/orders` (`checkout`, `my-orders`, `{id}`, `assign-child`, `cancel`) | Đã nối; `teacher-orders` chưa dùng |
| Thanh toán | `/api/v1/payments` (`create`, `{id}/status`) | Đã nối; webhook do PayOS gọi |
| Giỏ hàng | `/api/v1/cart` | Chưa (FE giữ giỏ trong bộ nhớ) |
| Hồ sơ | `/api/v1/users/me` (GET, PUT, logout, change-password) | Chưa |
| Khóa học, bài học, dụng cụ (quản lý) | `/api/courses`, `/api/lessons`, `/api/supplies`, `/api/lesson-supplies` | Chưa |
| Học tập | `/api/enrollments`, `/api/child-progress`, `/api/submissions`, `/api/v1/course-reviews` | Chưa |
| Tùy biến | `/api/customize-requests`, `/api/offers` | Chưa |
| Tin nhắn, thông báo | `/api/conversations`, `/api/messages`, `/api/v1/notifications` | Chưa |
| Giao hàng, hoàn tiền | `/api/v1/fulfillment`, `/api/v1/refunds` | Chưa |
| Tài chính | `/api/v1/teacher/finance`, `/api/v1/admin/finance`, `/api/v1/admin/analytics` | Chưa |
| Kiểm duyệt, staff, admin | `/api/v1/moderation`, `/api/moderation-reviews`, `/api/v1/staff`, `/api/v1/admin/users` | Chưa |
| Video | `/api/videos/upload`, `/api/videos/lessons/{lessonId}/upload`, `/api/videos/hls/{hlsId}/{fileName}` | Chưa. FE định gọi `/videos/upload-url` (R2 presigned) mà BE chưa có |

## Deploy Render, CORS và ngrok

Backend đang chạy tại <https://prm-g6-be.onrender.com>. Gói miễn phí tự ngủ khi không có request, lần gọi đầu có thể chậm 30–60 giây.


- `config/CorsConfig.java` chỉ cho phép origin `http://localhost:*` và `http://127.0.0.1:*` (Flutter Web chạy local). Thêm origin khác vào `allowedOriginPatterns` khi deploy.
- Header được phép: `Authorization`, `Content-Type`, `Accept`, `Origin`, `ngrok-skip-browser-warning`. Client thêm header nào khác thì phải thêm vào `allowedHeaders`, nếu không trình duyệt chặn ở bước preflight.
- Chia sẻ backend qua ngrok: `ngrok http 8080`, giữ cửa sổ mở. Lỗi `ERR_NGROK_3200` nghĩa là tunnel đã tắt. `server.forward-headers-strategy=framework` giúp Swagger gọi đúng `https`.

## Cấu trúc chính

```
src/main/java/com/example/art_course/
├─ auth, user, child, course, lesson, video, ...      # BE1
├─ modules/{cart,order,payment,refund,withdrawal,finance,...}   # BE2
├─ contracts/        # interface + sự kiện dùng chung BE1 <-> BE2
├─ common/           # máy trạng thái, xử lý lỗi chung
├─ config/           # Security, CORS, JWT, ShedLock, ...
└─ jobs/             # job nền (ShedLock)
```

## Quy tắc nghiệp vụ quan trọng

- Một đơn hàng thuộc đúng một Teacher. Giỏ nhiều Teacher được tách thành nhiều đơn, mỗi đơn một khoản thanh toán.
- Tiền dùng `BigDecimal`, VND, làm tròn `HALF_UP`. Hoa hồng tính một lần lúc thanh toán và được lưu lại.
- Sổ cái tiền chỉ ghi thêm, không sửa hay xóa.
- Webhook thanh toán phải idempotent, xác thực chữ ký và khớp số tiền.
- Khóa học có thể đặt khi chưa chọn bé; chọn bé sau thanh toán bằng `assign-child`. Khóa chưa gán bé chưa được cấp quyền học và chưa đủ điều kiện giải ngân (trừ khi có đánh giá hoặc tới mốc tự giải ngân).
- Loại dòng đơn hàng (`COURSE` / `SUPPLY` / `CUSTOMIZE`) suy ra từ cột khóa ngoại, không lưu cột riêng. `OrderDetail.getProductType()` vẫn có trong JSON trả về.

## Tài liệu

- `AGENTS.md` — quy tắc bắt buộc cho dự án
- `docs/decisions.md` — các quyết định đã chốt
- `docs/api/README.md` — hướng dẫn Swagger
- `docs/contracts/jwt-contract.md` — hợp đồng JWT giữa BE1 và BE2
- `docs/open-questions.md`, `docs/handoff-be1.md` — câu hỏi mở và yêu cầu chuyển cho BE1
- `docs/ops/backup-restore-guide.md` — sao lưu và khôi phục
