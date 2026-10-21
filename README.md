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
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_FROM` | Tùy chọn (mặc định `smtp.gmail.com`, `587`, `MAIL_USERNAME`) |
| `VNPAY_*`, `MOMO_*` | Cổng thanh toán sandbox (tùy chọn) |
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

- Schema nền: `docs/ART_COUSE.sql`. Migration BE2: `src/main/resources/db/migration/` (`V1__be2_schema_init.sql`, `V2__ledger_hardening.sql`).
- Flyway chỉ bật ở profile `dev`. Hibernate `ddl-auto` là `none` hoặc `validate`.
- **Không** chạy app, test hay migration vào database Supabase. Muốn áp migration lên Supabase, xuất file SQL rồi chạy thủ công.
- Script dev (PowerShell). Chỉ cho phép database `art_course_dev` trên localhost và cần biến `DEV_DB_PASSWORD`:
  `scripts/reset-dev-db.ps1`, `scripts/dev-up.ps1`, `scripts/dev-seed.ps1`, `scripts/dev-tokens.ps1`.

## Test

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests "com.example.art_course.modules.payment.PaymentFulfillmentTests"
```

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

## CORS và ngrok

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

## Tài liệu

- `AGENTS.md` — quy tắc bắt buộc cho dự án
- `docs/decisions.md` — các quyết định đã chốt
- `docs/api/README.md` — hướng dẫn Swagger
- `docs/contracts/jwt-contract.md` — hợp đồng JWT giữa BE1 và BE2
- `docs/open-questions.md`, `docs/handoff-be1.md` — câu hỏi mở và yêu cầu chuyển cho BE1
- `docs/ops/backup-restore-guide.md` — sao lưu và khôi phục
