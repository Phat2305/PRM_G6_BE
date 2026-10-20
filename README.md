# ART_COURSE — Backend (PRM_G6_BE)

Backend cho **Nền tảng khóa học vẽ và dụng cụ vẽ**: Teacher bán khóa học video và dụng cụ vẽ, Parent mua cho Child.
Hai nhóm backend (BE1: người dùng, khóa học, bài học; BE2: giỏ hàng, đơn hàng, thanh toán, dòng tiền) dùng chung một codebase và một database.

## Công nghệ

Java 21, Spring Boot 4.1.1 (Gradle), Spring Security + JWT (HS256), Spring Data JPA, PostgreSQL, Flyway, ShedLock, Testcontainers.

## Yêu cầu

- JDK 21
- PostgreSQL (local) hoặc Docker
- Docker (bắt buộc để chạy test, vì test dùng Testcontainers)

## Cấu hình

1. Sao chép `.env.example` thành `.env` và điền giá trị.
2. Ứng dụng tự nạp `.env` lúc khởi động; biến môi trường hệ thống (nếu có) thắng giá trị trong file.

| Biến | Ý nghĩa |
|------|---------|
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | Database mặc định (local) |
| `DATABASE_URL_DEV` | Database dev riêng, dùng với profile `dev` |
| `JWT_SECRET` | Khóa ký JWT, tối thiểu 32 ký tự |
| `VNPAY_*`, `MOMO_*` | Cổng thanh toán sandbox (tùy chọn) |
| `SUPABASE_JDBC_URL/DB_USERNAME/DB_PASSWORD` | Chỉ cho profile `supabase` |
| `PORT` | Cổng chạy, mặc định 8080 |

> Không commit `.env` hay bất kỳ thông tin đăng nhập thật nào.

## Chạy ứng dụng

```powershell
# Profile mặc định (database local, Flyway tắt)
.\gradlew.bat bootRun

# Profile dev: bật Flyway, dùng DATABASE_URL_DEV
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"

# Profile supabase: chỉ chạy runtime, không migrate
.\gradlew.bat bootRun --args="--spring.profiles.active=supabase"
```

Khi chạy `bootRun`, Gradle dừng ở `80% EXECUTING` — đó là bình thường, ứng dụng đang chạy. Dừng bằng `Ctrl+C`.

Swagger UI: <http://localhost:8080/swagger-ui.html>

## Database và migration

- Schema nền: `docs/ART_COUSE.sql`. Migration BE2 nằm ở `src/main/resources/db/migration/` (`V1__be2_schema_init.sql`, `V2__ledger_hardening.sql`).
- Flyway **tắt mặc định**, chỉ bật ở profile `dev`. Hibernate `ddl-auto` là `none`/`validate`.
- **Không** chạy app, test hay migration vào database Supabase. Muốn áp migration lên Supabase, xuất file SQL và chạy thủ công.
- Script dev (PowerShell, chỉ cho phép database `art_course_dev` trên localhost, cần `DEV_DB_PASSWORD`):
  `scripts/reset-dev-db.ps1`, `scripts/dev-up.ps1`, `scripts/dev-seed.ps1`, `scripts/dev-tokens.ps1`.

## Test

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests "com.example.art_course.modules.payment.PaymentFulfillmentTests"
```

## Xác thực

- `POST /auth/register` — đăng ký PARENT (ACTIVE) hoặc TEACHER (PENDING, chờ duyệt).
- `POST /auth/login` — trả JWT; chỉ tài khoản `ACTIVE` đăng nhập được.
- Gửi token qua header `Authorization: Bearer <token>`.
- Vai trò: PARENT, CHILD, TEACHER, STAFF, ADMIN. CHILD bị chặn ở các API đơn hàng, thanh toán, hoàn tiền, giao hàng và tài chính.

## Cấu trúc chính

```
src/main/java/com/example/art_course/
├─ auth, user, child, course, lesson, ...   # BE1
├─ modules/{cart,order,payment,refund,withdrawal,finance,...}   # BE2
├─ contracts/        # interface + sự kiện dùng chung BE1 <-> BE2
├─ common/           # máy trạng thái, xử lý lỗi chung
├─ config/           # Security, JWT, ShedLock, Jackson, ...
└─ jobs/             # job nền (ShedLock)
```

## Quy tắc nghiệp vụ quan trọng

- Một đơn hàng thuộc đúng một Teacher; giỏ nhiều Teacher được tách thành nhiều đơn, mỗi đơn một khoản thanh toán.
- Tiền dùng `BigDecimal`, VND, làm tròn `HALF_UP`; hoa hồng tính một lần lúc thanh toán và được lưu lại.
- Sổ cái tiền chỉ ghi thêm, không sửa/xóa.
- Webhook thanh toán phải idempotent, xác thực chữ ký và khớp số tiền.

## Tài liệu

- `AGENTS.md` — quy tắc bắt buộc cho dự án
- `docs/decisions.md` — các quyết định đã chốt
- `docs/api/README.md`, `docs/api/requests.http` — hướng dẫn Swagger và request mẫu
- `docs/contracts/jwt-contract.md` — hợp đồng JWT giữa BE1 và BE2
- `docs/open-questions.md`, `docs/handoff-be1.md` — câu hỏi mở và yêu cầu chuyển cho BE1
