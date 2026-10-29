# ART_COURSE — Backend (PRM_G6_BE)

Backend cho **Nền tảng khóa học vẽ và dụng cụ vẽ**: Teacher bán khóa học video và dụng cụ vẽ, Parent mua cho Child.
Một backend duy nhất (một codebase, một database), gồm:

- Tài khoản và nội dung: người dùng, hồ sơ, khóa học, bài học, tin nhắn, bài nộp, yêu cầu tùy biến.
- Mua bán và vận hành: giỏ hàng, đơn hàng, thanh toán, hoàn tiền, rút tiền, sổ cái tiền, kiểm duyệt, thông báo.

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

- Schema lịch sử: `docs/ART_COUSE.sql`; các bản bổ sung 01–09 nằm trong `docs/migrations/`.
- Migration mới nằm trong `src/main/resources/db/migration/`, áp trên schema hiện hành:

  | File | Nội dung |
  |------|----------|
  | `V10__parent_profile_address.sql` | Chuyển địa chỉ mặc định vào `parent_profiles.shipping_address`, bỏ bảng địa chỉ cũ |
  | `V11__commerce_safety.sql` | Lưu thông tin nhận hàng trong đơn, phân biệt hoàn từ tiền giữ/tiền khả dụng, sequence mã PayOS |

  Áp V11 trước khi triển khai bản backend này. SQL không sửa/xóa lịch sử sổ cái; số dư sai từ trước cần đối soát riêng.
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

Các test dùng PostgreSQL thật (Testcontainers) cần Docker; không có Docker thì chúng được đánh dấu bỏ qua, các test còn lại vẫn chạy. Test service dùng schema từ entity trong container riêng và áp SQL V11; test địa chỉ kiểm tra riêng V10.

## Xác thực

| Endpoint | Việc |
|----------|------|
| `POST /auth/register` | Đăng ký. `PARENT` vào trạng thái `ACTIVE`, `TEACHER` vào `PENDING` chờ duyệt |
| `POST /auth/login` | Trả JWT (`token`, `id`, `username`, `role`, `status`). Chỉ tài khoản `ACTIVE` đăng nhập được |
| `POST /auth/forgot-password` | Gửi mã xác nhận tới email |
| `POST /auth/reset-password` | Đặt mật khẩu mới bằng email + mã |

- Gửi token qua header `Authorization: Bearer <token>`. Token sống 24 giờ.
- Mật khẩu dài **6–20 ký tự** ở mọi API (đăng ký, quên/đặt lại, đổi mật khẩu, tạo và đặt lại mật khẩu con, tài khoản Staff). Không có mật khẩu mặc định.
- `POST /api/v1/users/me/logout` thu hồi **mọi** token của tài khoản (đăng xuất khỏi mọi thiết bị). Token cấp trước mốc thu hồi bị `JwtAuthFilter` từ chối.
- Vai trò: `PARENT`, `CHILD`, `TEACHER`, `STAFF`, `ADMIN`. `CHILD` bị chặn ở các API đơn hàng, thanh toán, hoàn tiền, giao hàng và tài chính.
- Request mẫu: `docs/api/requests.http`.

## Mua hàng và thanh toán PayOS

Tất cả endpoint dưới đây (trừ webhook, danh mục và trang kết quả) cần JWT của `PARENT`.

| Endpoint | Việc |
|----------|------|
| `GET /api/v1/shop/courses`, `GET /api/v1/shop/courses/{id}` | Danh mục khóa học (công khai). Có `teacherName` = `teacher_profiles.full_name` |
| `GET /api/v1/supplies`, `GET /api/v1/supplies/{id}` | Dụng cụ vẽ (công khai). Có `teacherName`. Chỉ trả dụng cụ `ACTIVE` hoặc `OUT_OF_STOCK`; bản chờ duyệt/bị ẩn trả 404 |
| `GET/POST /api/children`, `GET/PUT/DELETE /api/children/{id}` | Quản lý tài khoản con (BE1). `POST` bắt buộc `password` 6–20 ký tự |
| `PUT /api/children/{id}/password` | Phụ huynh đặt lại mật khẩu cho con, body `{"newPassword": "..."}` (6–20 ký tự), 204. Thu hồi token cũ của con |
| `PUT /api/children/{id}/lock` | Khóa/mở khóa con, body `{"locked": true}`. Khóa = `users.status` `LOCKED` (con không đăng nhập được, token đang dùng cũng bị chặn). Con bị admin đình chỉ thì trả 409. `ChildResponse` có trường `locked` |
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

Các biện pháp xử lý trùng và tiền đến muộn:

- Đơn đã hủy/hết hạn giữ nguyên trạng thái khi nhận tiền. Backend tạo một yêu cầu hoàn tiền để Staff xử lý, gồm cả phí giao hàng; không xuất kho hay cấp quyền học. API trạng thái trả `refundPending: true`.
- Tạo lại thanh toán dùng link PayOS cũ khi còn hiệu lực hoặc chưa xác định được trạng thái. Chỉ lấy mã mới khi PayOS xác nhận link cũ đã hủy/hết hạn.
- Hoàn tiền chỉ nhận giao dịch `HELD`/`RELEASED`, mỗi dòng chỉ có một yêu cầu chưa bị từ chối. Khóa đơn và yêu cầu rút tiền ngăn xử lý đồng thời hai lần.
- Hoàn từ `HELD` ghi `REFUND` với `affects_available=false`, giảm phần đang giữ; hoàn từ `RELEASED` giảm phần khả dụng. Sổ cái ghi phần giáo viên chịu, yêu cầu hoàn vẫn ghi đủ tiền trả phụ huynh. Hoàn hết đổi payment sang `REFUNDED`.
- Giải ngân chạy từng đơn qua transaction riêng. Cấp quyền học lỗi được thử lại từ đơn đã lưu mỗi phút, kể cả sau restart; không tự mở lại ghi danh bị vô hiệu hóa hoặc dòng đã hoàn tiền.
- Đơn dụng cụ bắt buộc có `recipientName`, `recipientPhone`, `shippingAddress`; thông tin được lưu theo thời điểm đặt. `/api/videos/upload` chỉ cho `TEACHER`.

Cấu hình PayOS:

- Dán URL webhook vào kênh thanh toán trên PayOS: `https://prm-g6-be.onrender.com/api/v1/payments/webhook/PAYOS` (hoặc URL ngrok khi chạy local). Khi lưu, PayOS gửi một webhook mẫu; backend trả 200.
- Hiện chỉ nhận cổng `PAYOS`. Lớp VNPay/MoMo/Mock vẫn còn trong code, nhưng API tạo thanh toán từ chối cổng khác.
- Mỗi lần tạo link dùng một `orderCode` mới (lưu ở `payments.gateway_order_code`), nên đơn chưa trả có thể thanh toán lại.

## API backend và trạng thái nối với FE

Danh sách đầy đủ xem Swagger. Bảng dưới cho biết app Flutter (`PRM393_G6_FE`) đã gọi những endpoint nào, đối chiếu với mã nguồn FE (cập nhật 24/10/2026).

- **Đã nối**: FE gọi các endpoint chính của nhóm.
- **Một phần**: chỉ một vài endpoint, hoặc chỉ một vai trò; phần còn lại FE vẫn dùng dữ liệu mẫu.
- **Chưa**: FE chưa gọi.

| Nhóm | Endpoint | FE |
|------|----------|----|
| Xác thực | `/auth/login`, `/auth/register`, `/auth/forgot-password`, `/auth/reset-password` | Đã nối |
| Hồ sơ | `/api/v1/users/me` (GET, PUT), `/me/logout`, `/me/change-password` | Đã nối. Đăng xuất gọi `logout` trước khi xóa token cục bộ; lỗi mạng thì bỏ qua |
| Danh mục công khai | `/api/v1/shop/courses`, `/api/v1/shop/courses/{id}`, `/api/v1/supplies` | Đã nối. Chưa dùng: `shop/courses/lessons/{id}/trial`, `supplies/{id}` |
| Con | `/api/children` (GET, POST, `PUT`/`DELETE` theo `{id}`), `PUT /{id}/password`, `PUT /{id}/lock` | Đã nối, kể cả `GET /{userId}` (làm mới hồ sơ một bé khi phụ huynh mở màn tiến độ). Cờ "cho giáo viên xem thông tin" (`shareInfo`) chưa có cột ở BE nên chỉ giữ trong phiên (xem `docs/handoff-be1.md`) |
| Giỏ hàng | `/api/v1/cart` (GET, `items` POST/PATCH/DELETE) | Một phần: dụng cụ lưu trên server; khóa học chưa chọn bé chỉ ở bộ nhớ app vì `cart_items` bắt buộc `childId`. Khi mua FE gửi `checkout` với `useCart:false` rồi tự xóa dòng giỏ sau khi thanh toán |
| Đơn hàng | `/api/v1/orders` (`checkout`, `my-orders`, `teacher-orders`, `{id}`, `assign-child`, `cancel`) | Đã nối |
| Thanh toán | `/api/v1/payments` (`create`, `{id}/status`) | Đã nối; webhook do PayOS gọi. FE dùng trang tĩnh `/payment-result.html` thay cho `return/{gateway}` |
| Khóa học, bài học, dụng cụ (giáo viên) | `/api/courses` (`teacher/{id}`, POST, PUT, DELETE), `/api/lessons` (`course/{id}`, `{id}`, POST, PUT, DELETE), `/api/supplies` (`teacher/{id}`, POST, PUT, DELETE), `/api/lesson-supplies` | Đã nối. Chưa dùng các `GET` danh sách/chi tiết công khai của `/api/courses` và `/api/supplies` |
| Video | `/api/videos/lessons/{lessonId}/upload`, `/status`, `/api/videos/hls/{hlsId}/{fileName}` | Đã nối (tải lên, hỏi trạng thái, phát HLS có Bearer). Chưa dùng `/api/videos/upload` |
| Bài nộp | `/api/submissions` | Một phần: giáo viên `GET /lesson/{id}` và `PUT /{id}/grade`. Nộp bài (`POST`) chưa |
| Tùy biến | `/api/customize-requests`, `/api/offers` | Một phần (phía giáo viên): `customize-requests` `status/pending` và `{id}`, `offers` `POST`, `request/{id}`, `teacher/{id}`. Phụ huynh tạo/sửa yêu cầu chưa |
| Tin nhắn | `/api/conversations`, `/api/messages` | Một phần: `POST /conversations`, `teacher/{id}`, `context`, `POST /messages`, `messages/conversation/{id}`. Hộp thư của phụ huynh (`conversations/parent/{id}`) chưa |
| Giao hàng | `/api/v1/fulfillment` | Một phần: giáo viên (`confirm-or-reject`, `shipping`, `delivered`) và phụ huynh (`parent/.../confirm-received`, `parent/.../cancel`). Chưa: `staff/.../handle-failure` |
| Tài chính giáo viên | `/api/v1/teacher/finance` (`balance`, `withdrawals` GET/POST) | Đã nối |
| Tiến độ học | `/api/child-progress` (`child/{id}`, `child/{id}/lesson/{id}`, `child/{id}/course/{id}`, `course/{id}`) | Đã nối: bé đăng nhập nạp tiến độ và tự gửi `PUT` khi xem video (mỗi ~15 giây, khi xem hết, khi đóng; mất mạng thì xếp hàng gửi lại; mở lại bài thì tua tới chỗ dừng); phụ huynh xem tiến độ từng con và tải lại mỗi khi mở màn; giáo viên xem học viên của khóa qua `course/{id}` (chỉ có mã bé, chưa có tên). Dùng kèm `GET /api/enrollments/child/{id}` để biết bé đang học khóa nào |
| Quyền học | `/api/enrollments` | `GET child/{id}` đã nối (bé xem của mình, phụ huynh xem của con). Không có endpoint tạo: quyền học chỉ được cấp khi thanh toán thành công. `GET course/{id}` chỉ giáo viên của khóa; `PATCH .../status` chỉ Staff/Admin; FE chưa dùng hai endpoint này |
| Đánh giá khóa | `/api/v1/course-reviews/course/{id}` (GET, POST) | Đã nối: phụ huynh đánh giá ở chi tiết đơn (mỗi khóa một lần, cần con đang học khóa) và xem ở tab Đánh giá; giáo viên xem đánh giá khóa của mình (`GET` chỉ cho PARENT hoặc TEACHER sở hữu khóa) ở chi tiết khóa và mục "Đánh giá gần đây". Phản hồi không có mã phụ huynh/bé, chỉ có cờ `mine`. Danh mục `GET /api/v1/shop/courses` và chi tiết có thêm `averageRating` (null nếu chưa có, làm tròn 1 chữ số) và `reviewCount`. Controller đánh giá cũ của BE1 (`POST /`, `PUT/DELETE /{id}`, `child/{id}`, `parent/{id}`) đã xóa vì không kiểm tra chủ sở hữu và cho đánh giá không cần quyền học |
| Hoàn tiền | `/api/v1/refunds` | Chưa |
| Thông báo | `/api/v1/notifications` | Chưa |
| Tài chính admin | `/api/v1/admin/finance` (`withdrawals` GET, `approve`, `reject`, `paid`, `configs` GET/PUT), `/api/v1/admin/analytics/finance` | Đã nối cho Admin: màn Tổng quan (số liệu dòng tiền thật; biểu đồ doanh thu theo tháng chưa có API nên ẩn), Rút tiền (duyệt, từ chối kèm lý do, ghi mã giao dịch khi đã chuyển khoản) và Cấu hình (hoa hồng nhập theo %, các mốc thời gian, phí ship). `GET withdrawals` có thêm `teacherName`. `PUT configs/{key}` kiểm tra giá trị theo từng khóa (hoa hồng 0-1, số ngày nguyên, N/X cho trống = tắt) và từ chối khóa lạ. `analytics/ledger-audit` chưa có màn |
| Tài khoản Staff, duyệt giáo viên | `/api/v1/admin/users` (`staff` CRUD, `staff/{id}/status`, `teachers/pending`, `teachers/{id}/approve`) | Đã nối cho Admin: tạo, sửa, khóa, xóa Staff (BE chỉ có tên đăng nhập và mật khẩu, chưa có họ tên, email hay phân quyền chi tiết) và màn Duyệt giáo viên (chỉ có tên đăng nhập, chưa có hồ sơ chứng chỉ) |
| Kiểm duyệt, Staff | `/api/v1/moderation`, `/api/moderation-reviews`, `/api/v1/staff` | Chưa |

## Cập nhật đợt 27/10/2026

Một số bảng ở trên được viết trước đợt này nên có chỗ cũ (ví dụ trạng thái nối với FE); phần dưới và `docs/api/README.md`, `docs/decisions.md` là bản mới nhất.

- **Gỡ khóa công khai vi phạm (STF-06).** `GET /api/v1/staff/courses/published` liệt kê khóa đang công khai; `POST /api/v1/staff/courses/{id}/hide` (body `reason`) đổi status sang `HIDDEN`, không xóa dữ liệu. Ghi `moderation_reviews` với quyết định `FLAGGED`, gửi thông báo `COURSE_HIDDEN` cho giáo viên. Khóa `HIDDEN` biến khỏi danh mục, giỏ và đặt hàng nhưng người đã mua vẫn học được; giáo viên sửa rồi gửi duyệt lại. Khóa customize không gỡ được (409). Cột `courses.status` là `varchar(20)` không có CHECK trong schema nền nên không cần SQL; nếu database của bạn có thêm ràng buộc trạng thái thì phải thêm `HIDDEN`.
- **Ảnh bìa khóa học bắt buộc khi gửi duyệt.** `CourseReviewPolicy.requireReviewable` từ chối gửi duyệt và duyệt khóa thường chưa có `cover_image_url` (400 "Khóa học cần có ảnh bìa"); khóa customize được miễn. Ảnh tải bằng `POST /api/courses/{id}/cover` (JPG/PNG/WEBP, tối đa 5 MB) và phải lên trước khi gửi duyệt. Khóa cũ không có ảnh sẽ bị yêu cầu thêm ảnh khi gửi duyệt lại.
- **Mật khẩu.** `POST /api/v1/users/me/change-password` từ chối mật khẩu mới trùng mật khẩu cũ (`SAME_PASSWORD`) và tài khoản bé (403 `CHILD_PASSWORD_BY_PARENT`, bé do phụ huynh đặt lại). `POST /auth/reset-password` so `newPassword` với `confirmPassword` (400 `PASSWORD_MISMATCH`, không tính vào số lần nhập sai mã).
- **SQL phải chạy trước** (SQL Editor của Supabase hoặc profile `dev`): `docs/migrations/07_review_workflow.sql` và `docs/migrations/09_fe_gaps_2.sql` (cùng `08_fe_gaps.sql` nếu chưa chạy).
- **Chưa làm / cần lưu ý:** mã quên mật khẩu còn lưu trong bộ nhớ (`PasswordResetRepository`, mất khi restart); chưa giới hạn số lần đăng nhập sai; chưa lưu việc đồng ý điều khoản và dữ liệu trẻ em (ACC-16/17); quy tắc mật khẩu BE chỉ kiểm độ dài 6–20 (app yêu cầu thêm chữ và số); video lỗi chưa có DLQ/retry; thanh toán chỉ có PayOS; test dùng Testcontainers cần Docker; xem số test bị bỏ qua trong báo cáo Gradle; nên bật RLS cho các bảng Supabase.

## Deploy Render, CORS và ngrok

Backend đang chạy tại <https://prm-g6-be.onrender.com>. Gói miễn phí tự ngủ khi không có request, lần gọi đầu có thể chậm 30–60 giây.


- `config/CorsConfig.java` chỉ cho phép origin `http://localhost:*` và `http://127.0.0.1:*` (Flutter Web chạy local). Thêm origin khác vào `allowedOriginPatterns` khi deploy.
- Header được phép: `Authorization`, `Content-Type`, `Accept`, `Origin`, `ngrok-skip-browser-warning`. Client thêm header nào khác thì phải thêm vào `allowedHeaders`, nếu không trình duyệt chặn ở bước preflight.
- Chia sẻ backend qua ngrok: `ngrok http 8080`, giữ cửa sổ mở. Lỗi `ERR_NGROK_3200` nghĩa là tunnel đã tắt. `server.forward-headers-strategy=framework` giúp Swagger gọi đúng `https`.

## Cấu trúc chính

```
src/main/java/com/example/art_course/
├─ auth, user, child, course, lesson, video, ...      # tài khoản, nội dung
├─ modules/{cart,order,payment,refund,withdrawal,finance,...}   # mua bán, vận hành
├─ contracts/        # interface + sự kiện dùng chung giữa các module
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
- Trạng thái dụng cụ do server quyết định, client gửi `status` cũng bị bỏ qua:
  - Tạo mới luôn là `PENDING_REVIEW`; Staff duyệt (`APPROVE`) mới thành `ACTIVE`. Đổi tên, mô tả, ảnh hoặc giá thì quay lại `PENDING_REVIEW`.
  - Tồn kho về 0 thì `ACTIVE` tự chuyển `OUT_OF_STOCK`; có hàng lại (giáo viên nhập thêm, hủy/hoàn đơn) thì tự về `ACTIVE`, không cần duyệt lại. `PENDING_REVIEW`, `REJECTED`, `HIDDEN` không bị đổi theo tồn kho.
  - Danh mục `/api/v1/supplies` trả cả `ACTIVE` và `OUT_OF_STOCK` (FE hiện "Hết hàng"); giỏ và đặt hàng từ chối dụng cụ không ở `ACTIVE`.
  - Thanh toán trừ `stock_quantity` (giữ chỗ → đã bán); tồn khả dụng = `stock_quantity - reserved_quantity`.
- Tiến độ học (`/api/child-progress`): chỉ chính bé ghi được tiến độ của mình (`PUT`), và chỉ cho bài học thử hoặc bài của khóa bé đã ghi danh (`NOT_ENROLLED` nếu không). Xem được: bé, phụ huynh của bé, và giáo viên của khóa (chỉ theo bài hoặc theo khóa); `GET /course/{id}` trả cả danh sách bé đang học khóa kèm số bài xong, chỉ cho giáo viên sở hữu khóa. `isCompleted=true` chỉ được lưu khi bé đã xem ít nhất 90% thời lượng bài (`lessons.duration_seconds`, điền khi FFmpeg xử lý xong; bài chưa có thời lượng thì không kiểm; áp cột bằng `docs/migrations/05_lesson_duration.sql`). Ghi bằng một câu `INSERT ... ON CONFLICT`: giây xem không bao giờ giảm, bài đã hoàn thành không quay lại chưa hoàn thành, hai yêu cầu đầu tiên cùng lúc không đụng khóa chính. `watchedSeconds` tối đa 86400.
- Loại dòng đơn hàng (`COURSE` / `SUPPLY` / `CUSTOMIZE`) suy ra từ cột khóa ngoại, không lưu cột riêng. `OrderDetail.getProductType()` vẫn có trong JSON trả về.

## Tài liệu

- `AGENTS.md` — quy tắc bắt buộc cho dự án
- `docs/decisions.md` — các quyết định đã chốt
- `docs/api/README.md` — hướng dẫn Swagger
- `docs/contracts/jwt-contract.md` — hợp đồng JWT
- `docs/open-questions.md` — câu hỏi mở (`docs/handoff-be1.md` là ghi chép cũ từ thời còn chia BE1/BE2)
- `docs/ops/backup-restore-guide.md` — sao lưu và khôi phục
