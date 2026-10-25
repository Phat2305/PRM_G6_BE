# Danh sách bàn giao Backend 1 (Handoff BE1)

Codebase và Database chung giữa BE1 và BE2. BE2 không tự ý chỉnh sửa schema của BE1. Tài liệu này liệt kê các điểm BE1 cần hỗ trợ hoặc cập nhật theo kết quả Audit (Prompt 1).

---

## 1. Hợp đồng giao tiếp (Contracts Package: `src/main/java/contracts` hoặc `contracts`)
BE2 cung cấp package contracts để đồng bộ giữa hai bên:

### BE2 gọi BE1:
- `grantAccess(UUID childId, UUID courseId, UUID orderId)`:
  - Cấp quyền truy cập học cho tài khoản con ngay khi đơn hàng thanh toán thành công (PAY-03, BR-01).
- `revokeAccess(UUID childId, UUID courseId, UUID refundId)`:
  - Thu hồi quyền học khi khoản thanh toán của khóa học bị hoàn tiền (FIN-09, BR-12).
- `isEnrolled(UUID childId, UUID courseId): boolean`:
  - Kiểm tra xem tài khoản con đã sở hữu khóa học này chưa để BE2 chặn mua trùng trong giỏ hàng (SHOP-04).

### BE2 nhận sự kiện từ BE1:
- `course_completed (childId, courseId)`:
  - Phát sinh khi tài khoản con hoàn thành mọi bài học trong khóa (LRN-10). BE2 dùng để kích hoạt giải phóng tiền escrow (mục 7.1).
- `course_reviewed (parentId, courseId, rating, comment)`:
  - Phát sinh khi Parent gửi đánh giá sao/nhận xét (SHOP-09). BE2 dùng để kích hoạt giải phóng tiền escrow (mục 7.1).
- `customize_offer_accepted (parentId, teacherId, requestId, offerId, amount)`:
  - Phát sinh khi Parent chấp nhận báo giá customize (CUS-08). BE2 dùng để tạo đơn hàng chờ thanh toán.
- `customize_completed (requestId, courseId)`:
  - Phát sinh khi khóa học customize hoàn thành / được phụ huynh xác nhận nghiệm thu (CUS-12). BE2 giải phóng tiền escrow.
- `submission_created`, `submission_graded`, `message_sent`:
  - Các sự kiện để notification service của BE2 phát thông báo tới Parent/Teacher.

### BE1 gọi BE2:
- `recordModerationReview(staffId, targetType, targetId, decision, reason)`:
  - BE1 gọi service của BE2 để lưu nhật ký khi Staff duyệt/từ chối hồ sơ giáo viên, duyệt khóa học hoặc duyệt yêu cầu customize (STF-07, `moderation_reviews`).

---

## 2. Lưu ý về Schema và Bảng dữ liệu thuộc BE1
- **Bảng `courses`**:
  - Theo SRS CRS-01 và Quyết định #1: Khóa học không có trường trình độ (`level`). Đề nghị BE1 bỏ `level` trong validation và schema.
  - Khóa học chỉ công khai khi Staff duyệt và mọi video ở trạng thái `READY` (CRS-05, Quyết định #3).
  - Cột `customize_request_id` trong `courses` dùng để liên kết ngược lại yêu cầu customize khi Teacher hoàn tất khóa học theo yêu cầu.
- **Bảng `child_enroll` (quyền học)**:
  - Trạng thái quyền học cần hỗ trợ `ACTIVE` và `REVOKED` (SRS 8.1, BR-12, FIN-09).
  - Khi BE2 gọi `grantAccess`, bản ghi sẽ được tạo hoặc cập nhật thành `ACTIVE`. Khi `revokeAccess`, cập nhật sang `REVOKED`.
- **Bảng `children_course`**:
  - BE2 đề xuất loại bỏ bảng này trong schema, vì thông tin Child được gán khóa học đã được lưu trực tiếp trong cột `orders_detail.child_id`.
- **Bảng `supplies` (Dụng cụ)**:
  - BE2 bổ sung 2 cột `reserved_quantity` (tồn giữ chỗ) và `sold_quantity` (tồn đã bán).
  - BE1 khi hiển thị số lượng tồn kho khả dụng cho khách hàng vui lòng tính:  
    `available_stock = stock_quantity - reserved_quantity`.
- **Bảng `conversations` & `messages`**:
  - Thuộc quyền sở hữu của BE1. Staff chỉ được đọc nội dung cuộc trao đổi khi cần giải quyết khiếu nại liên quan (MSG-06).
- **Bảng `child_profiles`**:
  - Child tuyệt đối không có quyền gọi các endpoint giỏ hàng, đặt hàng, thanh toán, ví tiền (BR-02, NFR-04). Security filter của BE2 sẽ từ chối token của vai trò Child.
- **Bảng cũ `"Payment"` và `orders_detail` (từ schema nền `ART_COUSE.sql`)**:
  - BE2 giữ nguyên không xóa hai bảng cũ `"Payment"` và `orders_detail` để không làm gãy code/script cũ của BE1 nếu còn tham chiếu.
  - Toàn bộ nghiệp vụ BE2 sử dụng hai bảng chuẩn hóa theo SRS và Quyết định đã chốt: `payments` và `order_details`. Đề nghị BE1 nếu có truy vấn sang chi tiết đơn hoặc thanh toán thì chuyển sang đọc từ `order_details` và `payments`.

---

## 3. Kiến trúc Package & Vị trí 15 Skeleton Controller

### Hiện trạng Package
- **Lớp khởi động chính**: `ArtCourseApplication` nằm tại package `com.example.art_course` (`src/main/java/com/example/art_course/ArtCourseApplication.java`).
- Theo cơ chế mặc định của Spring Boot, `@SpringBootApplication` chỉ thực hiện component scan đối với package `com.example.art_course.*` và các package con của nó.
- **15 Skeleton Controller ban đầu** (từ commit `914afcd`) hiện nằm ở các package root độc lập ngoài `com.example.art_course`:
  1. `account.Controller.AccountController` (`src/main/java/account/Controller/AccountController.java`) — BE1
  2. `assignment.Controller.AssignmentController` (`src/main/java/assignment/Controller/AssignmentController.java`) — BE1
  3. `auth.Controller.AuthController` (`src/main/java/auth/Controller/AuthController.java`) — BE1
  4. `child.Controller.ChildController` (`src/main/java/child/Controller/ChildController.java`) — BE1
  5. `course.Controller.CourseController` (`src/main/java/course/Controller/CourseController.java`) — BE1 (độc quyền `/api/v1/courses/**`)
  6. `customize.Controller.CustomizeController` (`src/main/java/customize/Controller/CustomizeController.java`) — BE1
  7. `enrollment.Controller.EnrollmentController` (`src/main/java/enrollment/Controller/EnrollmentController.java`) — BE1
  8. `learning.Controller.LearningController` (`src/main/java/learning/Controller/LearningController.java`) — BE1
  9. `messaging.Controller.ConversationController` (`src/main/java/messaging/Controller/ConversationController.java`) — BE1
  10. `video.Controller.VideoController` (`src/main/java/video/Controller/VideoController.java`) — BE1
  11. `cart.Controller.CartController` (`src/main/java/cart/Controller/CartController.java`) — Skeleton BE2
  12. `order.Controller.OrderController` (`src/main/java/order/Controller/OrderController.java`) — Skeleton BE2
  13. `product.Controller.ProductController` (`src/main/java/product/Controller/ProductController.java`) — Skeleton BE2
  14. `payment.Controller.PaymentController` (`src/main/java/payment/Controller/PaymentController.java`) — Skeleton BE2
  15. `fulfillment.Controller.FulfilmentController` (`src/main/java/fulfillment/Controller/FulfilmentController.java`) — Skeleton BE2

> **Ghi chú bảo toàn:** BE2 tuyệt đối KHÔNG di chuyển hay chỉnh sửa bất kỳ file skeleton nào của BE1 trong 10 file kể trên.

---

### Hai (02) phương án đề xuất để BE1 điền `@RestController` mà vẫn được Spring quét

#### Phương án 1 (Khuyên dùng — Chuẩn hóa cây package `com.example.art_course`):
- BE1 chuyển các skeleton của mình vào cây package chuẩn:
  `com.example.art_course.be1.<module>.controller.<Name>Controller` (ví dụ `com.example.art_course.be1.auth.controller.AuthController`).
- **Ưu điểm**:
  - Tuân thủ quy ước đặt tên chuẩn Java (package viết thường).
  - Tự động được `@SpringBootApplication` quét và đăng ký bean mà không cần cấu hình thêm bất kỳ tham số nào.
  - Phân tách module rõ ràng, tránh xung đột namespace.

#### Phương án 2 (Giữ nguyên vị trí skeleton ở Root Package):
- Giữ nguyên toàn bộ 10 skeleton của BE1 tại `account.Controller`, `course.Controller`...
- Để Spring quét được khi BE1 gắn `@RestController`, cấu hình thuộc tính `scanBasePackages` tại `ArtCourseApplication.java`:
  ```java
  @SpringBootApplication(scanBasePackages = {
      "com.example.art_course",
      "account", "assignment", "auth", "child", "course",
      "customize", "enrollment", "learning", "messaging", "video",
      "cart", "order", "product", "payment", "fulfillment", "config"
  })
  public class ArtCourseApplication { ... }
  ```
- **Ưu điểm**: Không làm xáo trộn đường dẫn file cũ trong Git của BE1.
- **Lưu ý**: Cần liệt kê đầy đủ tất cả package root để không bỏ sót controller nào.

## Cờ "cho giáo viên xem thông tin" của con (shareInfo)
- FE màn "Con của tôi" có tuỳ chọn bật/tắt cho giáo viên xem thông tin của con, hiện chỉ lưu trong phiên vì `child_profiles` chưa có cột tương ứng.
- Đề xuất: thêm cột `share_info boolean not null default false` vào `child_profiles`, trả `shareInfo` trong `ChildResponse` và nhận trong `UpdateChildRequest`. Bảng thuộc BE1 nên BE2 không tự sửa.

## Thời lượng bài học (xác thực "đã học xong")
- Đã thêm cột `lessons.duration_seconds integer` (entity `Lesson`, `docs/ART_COUSE.sql`, script `docs/migrations/05_lesson_duration.sql`). **Cần BE1 xác nhận và chạy script này trên Supabase trước khi deploy bản có `Lesson.durationSeconds`**, nếu không mọi truy vấn `lessons` sẽ lỗi vì thiếu cột.
- `VideoProcessingService.convert` đọc thời lượng từ `ffmpeg -i` (cùng lần đọc kiểm tiếng) và `VideoLessonService.completeLessonVideo` ghi vào cột khi video `READY`.
- `PUT /api/child-progress/.../lesson/{id}`: `isCompleted=true` chỉ được lưu khi `max(giây đã xem trước đó, giây vừa báo)` đạt 90% thời lượng; chưa đủ thì chỉ lưu giây xem và trả `isCompleted=false`. Bài chưa có thời lượng (video cũ, `NULL`) không bị kiểm; xử lý lại video thì cột được điền.
- Giới hạn còn lại: `watchedSeconds` là vị trí phát do client báo, nên kéo thanh tua tới cuối vẫn vượt ngưỡng. Muốn chặn thì cần gửi thời gian xem thực (tích lũy) từ trình phát.
