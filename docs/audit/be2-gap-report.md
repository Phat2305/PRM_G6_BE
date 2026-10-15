# Báo cáo Audit Khoảng trống Backend 2 (BE2 Gap Report)
**Ngày thực hiện:** 2026-10-14  
**Người thực hiện:** Senior Backend Engineer (BE2)  
**Phạm vi:** Kiểm toán toàn diện hiện trạng mã nguồn, lược đồ cơ sở dữ liệu (`docs/ART_COUSE.sql`), sơ đồ ERD và đối chiếu với SRS bản thảo lần 3.

---

## 1. Xác định Stack Thực tế & Bằng chứng

| Thành phần | Hiện trạng thực tế | Bằng chứng mã nguồn | Ghi chú & Đánh giá |
|---|---|---|---|
| **Ngôn ngữ & Phiên bản** | Java 21 | `build.gradle` (dòng 13: `languageVersion = JavaLanguageVersion.of(21)`) | Dự án được khởi tạo chuẩn Java 21 LTS. |
| **Framework** | Spring Boot | `build.gradle` (dòng 3: `id 'org.springframework.boot' version '4.1.1'`) | Đang khai báo starter Spring Boot cơ bản. |
| **Build tool** | Gradle | `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat` | Sử dụng Gradle Wrapper. |
| **Công cụ migration** | **Chưa có** | `build.gradle` chưa có dependency Flyway hoặc Liquibase | Cần đề xuất Flyway để quản lý phiên bản database độc lập và an toàn. |
| **Kiểu Auth** | **Chưa triển khai** | `src/main/java/config/SecurityConfig.java` (lớp rỗng 3 dòng), `auth/Controller/AuthController.java` (rỗng) | Chưa có Spring Security / JWT filter. |
| **Cách truy cập DB** | **Chưa triển khai** | `src/main/java/config/JpaConfig.java` (lớp rỗng 4 dòng) | Chưa có dependency Spring Data JPA / JDBC / PostgreSQL driver trong `build.gradle`. |
| **Tổ chức Module** | Theo package chức năng ở root `src/main/java/` | Các package: `order/Controller`, `cart/Controller`, `payment/Controller`, `fulfillment/Controller`, v.v. | Tên thư mục `Controller` đang viết hoa (chưa theo chuẩn quy ước camelCase của package Java). |
| **Format Response / Lỗi** | **Chưa triển khai** | `src/main/java/config/JacksonConfig.java` (lớp rỗng) | Chưa có `@ControllerAdvice`, `GlobalExceptionHandler` hoặc DTO ApiResponse chung. |
| **Test Framework** | JUnit 5 | `build.gradle` (dòng 25: `spring-boot-starter-test`, dòng 32: `useJUnitPlatform()`) | Đã sẵn sàng hạ tầng JUnit Platform. |
| **Cách chạy dự án** | Gradle CLI | `./gradlew bootRun` hoặc `gradlew.bat bootRun` | Đang chạy với class chính `com.example.art_course.ArtCourseApplication`. |

> [!WARNING]
> **Điểm lệch giữa tài liệu hướng dẫn và mã nguồn:**
> File `AGENTS.md` (dòng 1–3) ghi: *"Backend thực tế là Node.js + TypeScript + Express..."*. Tuy nhiên, toàn bộ repository trên đĩa thực tế hiện tại là **Java 21 + Spring Boot + Gradle**, hoàn toàn không có `package.json`, `tsconfig.json` hay file `.ts` nào. Toàn bộ các controller hiện tại đều là skeleton Java. Báo cáo audit này được thực hiện trên nền tảng mã nguồn thực tế của repo.

---

## 2. Ánh xạ Module/Controller Hiện có vào Mã SRS của BE2

| Module / Controller | Phạm vi SRS BE2 | Trạng thái mã nguồn | Phân loại | Lý do & Hành động cần làm |
|---|---|---|---|---|
| `cart/Controller/CartController.java` | SHOP-04, SHOP-05 | Lớp rỗng (3 dòng) | **VIẾT LẠI / TRIỂN KHAI MỚI** | Chưa có logic. Cần xây dựng CartService, CartItem DTO, kiểm tra tồn kho realtime, chặn mua trùng 1 khóa cho cùng 1 Child (SHOP-04). |
| `order/Controller/OrderController.java` | SHOP-05, SHOP-08, BR-04, PAY-07, PAY-08 | Lớp rỗng (3 dòng) | **VIẾT LẠI / TRIỂN KHAI MỚI** | Chưa có logic. Cần cài đặt thuật toán tách đơn theo từng Teacher, tính phí ship cố định cho đơn có dụng cụ (#8), snapshot thông tin sản phẩm, giữ chỗ kho trong transaction. |
| `payment/Controller/PaymentController.java` | PAY-01..11, FIN-12, BR-11 | Lớp rỗng (3 dòng) | **VIẾT LẠI / TRIỂN KHAI MỚI** | Chưa có logic. Cần xây dựng PaymentGateway interface (hỗ trợ adapter VNPay/MoMo sandbox), webhook idempotent, xác thực chữ ký số, tính hoa hồng lúc thanh toán. |
| `fulfillment/Controller/FulfilmentController.java` | FUL-01..08 | Lớp rỗng (3 dòng) | **VIẾT LẠI / TRIỂN KHAI MỚI** | Chưa có logic. Cần cài đặt flow xác nhận/từ chối của Teacher, cập nhật mã vận đơn, xác nhận nhận hàng của Parent, tự xác nhận sau M ngày. |
| `product/Controller/ProductController.java` | SHOP-01, CRS-08, FUL-01 | Lớp rỗng (3 dòng) | **MỞ RỘNG / VIẾT LẠI** | Cần triển khai API tìm kiếm/duyệt dụng cụ, liên kết gợi ý dụng cụ theo bài học (`lesson_supplies`), hiển thị tồn khả dụng (`stock_quantity - reserved_quantity`). |
| `refund` *(chưa có package)* | FIN-07..10, BR-12, FUL-06 | **Chưa có** | **CHƯA CÓ** | Cần tạo mới module xử lý yêu cầu hoàn tiền theo từng dòng sản phẩm (`refunds`, `refund_items`), thu hồi quyền học khi hoàn khóa học. |
| `withdrawal` *(chưa có package)* | FIN-01..06, BR-15, ADM-03 | **Chưa có** | **CHƯA CÓ** | Cần tạo mới module rút tiền cho Teacher: Teacher tạo yêu cầu, Admin duyệt, Staff thanh toán thủ công kèm chứng từ giao dịch. |
| `ledger` *(chưa có package)* | NFR-08, FIN-03, FIN-12 | **Chưa có** | **CHƯA CÓ** | Cần tạo mới module Sổ cái tài chính (`money_ledger`) chỉ-ghi-thêm (append-only) để truy vết toàn bộ biến động tiền. |
| `system_config` *(chưa có package)* | ADM-02, Mục 10 SRS | **Chưa có** | **CHƯA CÓ** | Cần tạo mới module cấu hình hệ thống: lưu tỷ lệ hoa hồng, thời hạn thanh toán đơn, mốc M, N, X. |
| `moderation` *(chưa có package)* | STF-07, NFR-08 | **Chưa có** | **CHƯA CÓ** | Cần tạo mới module ghi nhận và tra cứu nhật ký kiểm duyệt (`moderation_reviews`). |
| `notification` *(chưa có package)* | PAY-06, FUL-04, FIN-11 | **Chưa có** | **CHƯA CÓ** | Cần tạo mới dịch vụ thông báo (Push/Email) dùng chung với cơ chế thử lại (retry queue). |
| `scheduler` / `jobs` *(chưa có package)* | PAY-08, FUL-05, Mục 7.1 | **Chưa có** | **CHƯA CÓ** | Cần tạo package tập trung các scheduled jobs: hết hạn đơn trả kho, tự xác nhận giao hàng sau M ngày, giải phóng tiền escrow. Khóa job bằng PostgreSQL Advisory Lock. |
| **Các module thuộc BE1** (`account`, `assignment`, `auth`, `child`, `course`, `customize`, `enrollment`, `learning`, `messaging`, `video`) | Phạm vi BE1 | Lớp rỗng (3 dòng) | **GIỮ (CHỈ ĐỌC / BE1)** | Thuộc quyền sở hữu của BE1. BE2 chỉ đọc hoặc gọi qua hợp đồng `contracts`. |

---

## 3. So Sánh Schema Hiện Hành (`ART_COUSE.sql`) và ERD Đích / SRS v3

| Bảng dữ liệu | Schema hiện hành (`ART_COUSE.sql`) | Yêu cầu ERD đích & SRS v3 | Sai khác & Lỗi nghiêm trọng cần khắc phục |
|---|---|---|---|
| `orders` | Có các cột: `id`, `parent_id`, `teacher_id`, `total_amount`, `status`, `expired_at`, `shipping_fee`. | Khớp hầu hết các cột chính. Trạng thái cần chuẩn hóa: `PENDING_PAYMENT`, `PAID`, `EXPIRED`, `CANCELLED`. | Thiếu `created_at`, `updated_at`. Cần index tra cứu theo `parent_id`, `teacher_id`, `status`. |
| `orders_detail` | `id`, `order_id`, `course_id NOT NULL`, `supply_id NULL`, `sub_total`, `shipping_provider`, `tracking_code`, `status`, `quantity (NUMERIC)`, `unit_price (NUMERIC)`. | - `course_id` và `supply_id` phải nullable kèm `CHECK ((course_id IS NOT NULL AND supply_id IS NULL) OR (course_id IS NULL AND supply_id IS NOT NULL))`.<br>- Snapshot `product_name`.<br>- `commission_rate`, `commission_amount`, `net_amount`.<br>- Cột `child_id` cho dòng khóa học.<br>- `quantity` chuyển thành `INTEGER`. | **LỖI NGHIÊM TRỌNG:** `course_id NOT NULL` sẽ khiến hệ thống không thể tạo đơn hàng chỉ mua dụng cụ vẽ! Cần sửa `course_id` thành nullable và bổ sung CHECK constraint đúng một trong hai. |
| `"Payment"` | Tên bảng PascalCase có dấu ngoặc kép `"Payment"`. Cột: `id`, `order_id`, `gateway`, `gateway_transaction_id`, `amount`, `status`, `paid_at`, `created_at`, `commission_amount`. | - Đổi tên bảng thành `payments` (snake_case).<br>- Thêm `teacher_id`, `net_amount`, `gateway_fee (NULL)`, `payment_url (NULL)`, `released_at (NULL)`.<br>- Trạng thái: `PENDING`, `HELD`, `RELEASED`, `FAILED`, `REFUNDED`. | **LỖI:** Tên bảng viết hoa có dấu ngoặc kép không nhất quán. Thiếu `teacher_id`, `net_amount`, `released_at`. Thiếu ràng buộc `UNIQUE (order_id)` và `UNIQUE (gateway, gateway_transaction_id)`. |
| `refunds` | `id`, `parent_id`, `total_amount`, `status`, `expired_at`, `bank_info`, `updated_at`, `payment_id`. | - Gắn `payment_id`, `order_id`.<br>- Bổ sung bảng chi tiết `refund_items` để hoàn tiền theo từng dòng sản phẩm.<br>- Thêm `reason`, `refund_transaction_code`, `proof_image_url`, `handled_by`. | Không hỗ trợ hoàn tiền chi tiết theo dòng (thiếu `refund_items`). Thiếu thông tin người xử lý và mã giao dịch hoàn trả. |
| `withdrawal_requests` | `id`, `teacher_id`, `approved_by NOT NULL`, `total_amount`, `status BOOLEAN NOT NULL`, `proof_image_url`. | - `status VARCHAR(20)` (`PENDING`, `APPROVED`, `PAID`, `REJECTED`).<br>- `approved_by` phải NULLABLE (vì khi mới tạo chưa ai duyệt).<br>- Thêm `bank_info`, `rejected_reason`, `paid_by`, `transaction_code`, `created_at`, `updated_at`.<br>- Partial Unique Index: tối đa 1 yêu cầu PENDING/APPROVED trên mỗi Teacher. | **LỖI NGHIÊM TRỌNG:** `status` kiểu `BOOLEAN` không thể biểu diễn được 4 trạng thái nghiệp vụ. `approved_by NOT NULL` ngăn cản việc Teacher tạo yêu cầu mới. |
| `supplies` | `id`, `teacher_id`, `name`, `description`, `image_url`, `price`, `stock_quantity (INT)`, `status`. | Cần theo dõi tồn kho giữ chỗ và tồn kho đã bán để chống overselling khi tạo đơn đồng thời. | Thiếu cột `reserved_quantity (INT DEFAULT 0)` và `sold_quantity (INT DEFAULT 0)`, cùng ràng buộc `CHECK (stock_quantity >= reserved_quantity)`. |
| `system_config` | `key`, `value`, `total_amount NUMERIC NOT NULL`, `status VARCHAR(20) NOT NULL`, `created_at`. | Chỉ lưu cặp cấu hình hệ thống: `key (PK)`, `value (TEXT NOT NULL)`, `description`, `updated_at`, `updated_by`. | **LỖI:** Tồn tại cột rác `total_amount` và `status` do copy-paste nhầm schema. Cần loại bỏ. |
| `children_course` | `id`, `order_id`, `course_id`, `child_id`, `price`. | SRS v3 gán khóa học trực tiếp cho Child ngay tại dòng chi tiết đơn hàng (`orders_detail.child_id`). | Bảng này dư thừa nếu `child_id` được đặt trực tiếp trong `orders_detail`. |
| `money_ledger` | **Chưa có** | Sổ cái bất biến chỉ-ghi-thêm (append-only), chặn UPDATE/DELETE bằng database trigger. | Cần tạo mới hoàn toàn để tuân thủ NFR-08. |
| `webhook_logs` | **Chưa có** | Nhật ký webhook: lưu payload, chữ ký, kết quả xử lý, phục vụ kiểm toán và idempotency. | Cần tạo mới hoàn toàn. |
| `carts` & `cart_items` | **Chưa có** | Lưu trữ giỏ hàng của phụ huynh trên server. | Cần tạo mới. |

---

## 4. Các Điểm Code & Mô Hình Cũ Mâu Thuẫn Quyết Định Đã Chốt

1. **Bỏ khái niệm Đơn con (`sub_orders`)**:  
   Trong ERD sơ khai có phân tách `orders` -> `sub_orders`. Tuy nhiên, theo SRS v3 (BR-04, #25) và Quyết định #5: Giỏ hàng có nhiều Teacher được tách thành **mỗi Teacher đúng một đơn hàng riêng biệt** ngay từ đầu (`orders.teacher_id`). Bảng `orders` trong `ART_COUSE.sql` đã có `teacher_id`, hoàn toàn phù hợp để loại bỏ hoàn toàn cấu trúc `sub_orders`.
2. **Hoàn tiền theo từng dòng chi tiết đơn (`order_details`)**:  
   Schema cũ chỉ có 1 bảng `refunds` chung chung gắn với payment. Quyết định đã chốt yêu cầu hoàn tiền chi tiết theo dòng sản phẩm cụ thể, bảo đảm tổng số tiền hoàn không vượt quá số tiền đã thanh toán của khoản payment đó.
3. **Giải phóng tiền Escrow**:  
   Mô hình cũ trong ERD sơ khai dùng `held_funds` và `payout_batches` theo kỳ. Quyết định đã chốt (#26 và Quyết định #6, #8): Giải phóng tiền cho toàn bộ khoản thanh toán (`payments`) khi **mọi dòng trong đơn đủ điều kiện**; Teacher rút tiền theo yêu cầu (`withdrawal_requests`).

---

## 5. Rà Soát Bảo Mật Tiền Tệ & Giao Dịch

1. **Xác thực Webhook & Idempotency**:
   - Hiện tại chưa có mã nguồn tiếp nhận webhook.
   - Cần bắt buộc tạo bảng `webhook_logs`, đặt chỉ mục duy nhất `UNIQUE (gateway, gateway_transaction_id)` trên bảng `payments` để chặn tuyệt đối việc cổng thanh toán gửi webhook lặp dẫn đến cộng tiền hai lần.
2. **Quản lý Transaction & Chống Bán Vượt Tồn (Race Conditions)**:
   - Thao tác tạo đơn và giữ chỗ tồn kho phải diễn ra trong một Database Transaction duy nhất.
   - Cập nhật tồn kho giữ chỗ bằng câu lệnh UPDATE có điều kiện:  
     `UPDATE supplies SET reserved_quantity = reserved_quantity + :qty WHERE id = :supplyId AND (stock_quantity - reserved_quantity) >= :qty;`  
     Nếu số dòng ảnh hưởng = 0, transaction lập tức rollback và báo lỗi hết hàng.
3. **Phân Quyền Vai Trò (Role-based Authorization)**:
   - Hiện chưa có cơ chế kiểm tra quyền. Cần chặn tuyệt đối vai trò `CHILD` không được phép gọi bất kỳ API tạo giỏ hàng, đặt đơn, thanh toán hay yêu cầu hoàn tiền (BR-02, NFR-04).
   - Staff chỉ được xem hội thoại khi có khiếu nại (MSG-06), không có quyền tự ý duyệt rút tiền (thuộc thẩm quyền Admin).

---

## 6. Các Điểm ERD Mơ Hồ Cần Quyết Định & Đề Xuất Giải Pháp

### (a) Child được gán khóa học lưu ở đâu khi tạo đơn: `orders_detail` hay `children_course`?
- **Đề xuất:** Lưu `child_id` trực tiếp trong bảng `orders_detail` (dưới dạng cột NULLABLE, bắt buộc có giá trị khi `course_id IS NOT NULL`).
- **Lý do:** Bỏ được bảng phụ `children_course`, truy vấn lịch sử đơn hàng của phụ huynh hiển thị ngay con nào học khóa nào mà không cần JOIN thêm 1 bảng trung gian.

### (b) Đơn customize liên kết yêu cầu customize thế nào khi khóa chưa tồn tại (#27)?
- **Đề xuất:** Thêm cột `customize_request_id (UUID FK NULL)` vào `orders_detail`. Khi phụ huynh chấp nhận báo giá (`customize_offer_accepted`), hệ thống tạo đơn hàng với dòng chi tiết có `customize_request_id`. Khi đơn được thanh toán thành công, yêu cầu customize chuyển sang `IN_PROGRESS`. Khi Teacher hoàn thành và đăng tải video, khóa học được tạo sẽ mang `customize_request_id` tương ứng (theo schema `courses.customize_request_id` đã có sẵn).

### (c) Tồn kho giữ chỗ/đã bán lưu ở cột hay bảng riêng?
- **Đề xuất:** Lưu trực tiếp 2 cột `reserved_quantity (INT DEFAULT 0)` và `sold_quantity (INT DEFAULT 0)` trong bảng `supplies`.
- **Lý do:** Đơn giản hóa truy vấn, tốc độ đọc sản phẩm nhanh, kiểm tra điều kiện tồn khả dụng (`stock_quantity - reserved_quantity >= qty`) cực kỳ dễ dàng và tin cậy trong câu lệnh UPDATE có điều kiện.

### (d) Giỏ hàng lưu server hay app?
- **Đề xuất:** Lưu giỏ hàng trên Server thông qua 2 bảng `carts` và `cart_items` gắn với `parent_id`.
- **Lý do:** Phụ huynh có thể chuyển đổi thiết bị (từ Mobile sang Web), đồng thời server có thể chủ động kiểm tra ràng buộc nghiệp vụ (SHOP-04: chặn thêm cùng 1 khóa học cho cùng 1 Child nếu đã mua hoặc đã có trong giỏ).

### (e) Đã có database dev riêng chưa (`DATABASE_URL_DEV`)?
- **Hiện trạng:** Kiểm tra trong thư mục dự án chưa có file `.env` hay `.env.local`.
- **Đề xuất:** Cần thiết lập file `.env.local` chứa biến `DATABASE_URL_DEV` trỏ tới cơ sở dữ liệu PostgreSQL cục bộ (Local PostgreSQL hoặc Docker) để sẵn sàng chạy migration và test tự động mà không ảnh hưởng tới Supabase thật.
