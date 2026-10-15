# Báo cáo Chuyển đổi Stack: Node.js/TypeScript → Spring Boot (Java)
**Tài liệu:** `docs/audit/stack-switch-report.md`  
**Dự án:** Nền tảng khóa học vẽ và dụng cụ vẽ (`D:\PRM_G6_BE`, nhánh `test-1`)  
**Người thực hiện:** Senior Backend Engineer (BE2)  
**Tham chiếu:** [prompt-switch-to-springboot.md](file:///D:/PRM_G6_BE/docs/prompts/prompt-switch-to-springboot.md), SRS v3 mục 2.2, [AGENTS.md](file:///D:/PRM_G6_BE/AGENTS.md).

---

## BƯỚC 1: KIỂM KÊ HIỆN TRẠNG (CHỈ ĐỌC)

### 1. Hiện trạng thư mục và công nghệ tại `D:\PRM_G6_BE`

| Thành phần | Hiện trạng phát hiện | Chi tiết / Đánh giá |
|---|---|---|
| **Build tool Java** | Có [build.gradle](file:///D:/PRM_G6_BE/build.gradle), [settings.gradle](file:///D:/PRM_G6_BE/settings.gradle), `gradlew`, `gradlew.bat` | Sử dụng Gradle 9.7.1 wrapper. |
| **Phiên bản Java** | Java 21 | Khai báo trong `build.gradle`: `JavaLanguageVersion.of(21)`. Lưu ý: JRE mặc định trên shell đang là 1.8, cần trỏ `JAVA_HOME` tới JDK 21. |
| **Spring Boot** | Có khai báo plugin Spring Boot | Đang ghi `id 'org.springframework.boot' version '4.1.1'` (phiên bản này không có trên Maven Central). Cần đưa về bản chuẩn **Spring Boot 3.3.4** (hỗ trợ Java 21 LTS). |
| **ORM / Data Access** | Chưa cấu hình dependency | Đang chỉ có file rỗng `JpaConfig.java`. Cần thêm `spring-boot-starter-data-jpa` và PostgreSQL JDBC Driver. |
| **Công cụ Migration** | Đã sẵn sàng file SQL chuẩn Flyway | Đã có [V1__be2_schema_init.sql](file:///D:/PRM_G6_BE/src/main/resources/db/migration/V1__be2_schema_init.sql) trong thư mục chuẩn `src/main/resources/db/migration/`. Cần thêm dependency `flyway-core` và `flyway-database-postgresql`. |
| **Kiểu Auth** | Chưa triển khai | Đang có file rỗng `SecurityConfig.java`. Cần `spring-boot-starter-security` và thư viện JJWT. |
| **Test Framework** | JUnit 5 | Đã có `spring-boot-starter-test` với `useJUnitPlatform()`. |
| **Cấu trúc Package gốc** | `com.example.art_course` | Entrypoint: `ArtCourseApplication.java`. Có 15 skeleton controller rỗng trong `src/main/java/*/Controller/`. |
| **Node.js / TS** | Vừa tạo tạm thời ở prompt 3 | Có `package.json`, `tsconfig.json`, `node_modules`, `src/**/*.ts`, `test/**/*.ts`. Cần chuyển vào `docs/legacy-ts/`. |

---

### 2. Bảng phân loại chi tiết từng File (so với Commit gốc `914afcd`)

| File / Thư mục | Nguồn gốc | Phân loại | Lý do & Hành động |
|---|---|---|---|
| `build.gradle`, `settings.gradle`, `gradlew*` | Có sẵn từ commit `914afcd` | **GIỮ & CẬP NHẬT** | Chuẩn hóa Spring Boot version sang 3.3.4, bổ sung dependencies Web, JPA, Security, Flyway, Postgres, ShedLock. |
| `src/main/java/com/example/art_course/ArtCourseApplication.java` | Có sẵn từ commit `914afcd` | **GIỮ NGUYÊN** | Main entrypoint của Spring Boot application. |
| `src/main/java/config/*` (`SecurityConfig`, `JpaConfig`,...) | Có sẵn từ commit `914afcd` | **GIỮ & TRIỂN KHAI** | Triển khai logic cấu hình chuẩn cho Spring Boot. |
| `src/main/java/*/Controller/*Controller.java` | Có sẵn từ commit `914afcd` | **GIỮ & TÁI CẤU TRÚC** | Chuyển thành `@RestController` chuẩn Spring MVC, tuân thủ quy ước package camelCase. |
| `src/main/resources/application.properties` | Có sẵn từ commit `914afcd` | **GIỮ & CẬP NHẬT** | Cấu hình DataSource, Flyway, JPA Hibernate, JWT secret, server port. |
| `src/test/java/.../ArtCourseApplicationTests.java` | Có sẵn từ commit `914afcd` | **GIỮ NGUYÊN** | Test context loads của Spring Boot. |
| `docs/SRS_v3.pdf`, `docs/decisions.md`, `open-questions.md`, `handoff-be1.md`, `erd*` | Tài liệu chung | **GIỮ NGUYÊN** | Tài liệu đặc tả nghiệp vụ, độc lập với ngôn ngữ. |
| `docs/ART_COUSE.sql` | Dump DB hiện trạng | **GIỮ NGUYÊN** | Tham khảo hiện trạng schema cũ. |
| `docs/migrations/*.sql` | Sinh ra từ Prompt 2 | **GIỮ NGUYÊN** | File SQL migration, seed data và test constraints (độc lập với ngôn ngữ). |
| `src/main/resources/db/migration/V1__be2_schema_init.sql` | Sinh ra từ Prompt 2 | **GIỮ NGUYÊN** | File Flyway versioned migration chuẩn cho Spring Boot. |
| `src/common/money.ts`, `state-machines.ts` | Sinh ra từ Prompt 3 (TS) | **CHUYỂN SANG JAVA** | Port sang `Money.java` (`BigDecimal`), `OrderStatus.java`, `DeliveryStatus.java`, v.v. Code TS chuyển vào `docs/legacy-ts/`. |
| `src/middlewares/auth.middleware.ts`, `error.middleware.ts` | Sinh ra từ Prompt 3 (TS) | **CHUYỂN SANG JAVA** | Port sang Spring Security `JwtAuthFilter.java` và `@RestControllerAdvice GlobalExceptionHandler.java`. Code TS chuyển vào `docs/legacy-ts/`. |
| `src/contracts/*.ts` | Sinh ra từ Prompt 3 (TS) | **CHUYỂN SANG JAVA** | Port sang Java interface `IEnrollmentContract.java` và `ApplicationEvent`. Code TS chuyển vào `docs/legacy-ts/`. |
| `src/modules/cart/*.ts`, `orders/*.ts`, `courses/*.ts`, `products/*.ts` | Sinh ra từ Prompt 3 (TS) | **CHUYỂN SANG JAVA** | Port sang các Controller, Service, Repository, Entity, DTO Java. Code TS chuyển vào `docs/legacy-ts/`. |
| `test/commerce.test.ts`, `test/unit.test.ts` | Sinh ra từ Prompt 3 (TS) | **CHUYỂN SANG JAVA** | Port 5 test bắt buộc sang JUnit 5 (`CommerceTests.java`). Code TS chuyển vào `docs/legacy-ts/`. |
| `package.json`, `tsconfig.json`, `node_modules/` | Sinh ra tạm thời | **GỠ BỎ / ARCHIVE** | Gỡ khỏi build, archive vào `docs/legacy-ts/` để repo sạch chuẩn Java. |

---

## BƯỚC 2: KẾ HOẠCH CHUYỂN ĐỔI SANG SPRING BOOT (JAVA 21)

### 1. Kiến trúc & Cấu trúc Package đề xuất

Root package: `com.example.art_course`

```
src/main/java/com/example/art_course/
├── ArtCourseApplication.java
├── config/
│   ├── SecurityConfig.java              # Spring Security + JWT Filter, chặn Role CHILD (403)
│   ├── JpaConfig.java                   # Auditing, Transaction Manager
│   ├── ShedLockConfig.java              # Distributed lock cho Scheduled Jobs (Postgres JDBC)
│   ├── CorsConfig.java                  # Cấu hình CORS
│   └── OpenApiConfig.java               # Swagger UI / OpenAPI 3
├── common/
│   ├── Money.java                       # Lớp xử lý tiền VND chính xác (BigDecimal, ROUND_HALF_UP)
│   ├── ApiResponse.java                 # Standard Response wrapper
│   ├── GlobalExceptionHandler.java      # @RestControllerAdvice xử lý lỗi tập trung
│   ├── AppError.java                    # Custom RuntimeException kèm error code & HTTP status
│   └── statemachine/
│       ├── OrderStatus.java
│       ├── DeliveryStatus.java
│       ├── PaymentStatus.java
│       ├── WithdrawalStatus.java
│       └── StateMachineValidator.java
├── contracts/
│   ├── IEnrollmentContract.java         # Interface gọi sang BE1 (grantAccess, revokeAccess, isEnrolled)
│   └── events/
│       ├── CourseReviewedEvent.java     # Sự kiện BE1 -> BE2 (giải phóng escrow)
│       └── CourseCompletedEvent.java
├── modules/
│   ├── cart/                            # SHOP-04, ACC-04
│   │   ├── controller/CartController.java
│   │   ├── service/CartService.java
│   │   ├── entity/Cart.java, CartItem.java
│   │   ├── repository/CartRepository.java, CartItemRepository.java
│   │   └── dto/AddToCartRequest.java, CartResponse.java
│   ├── order/                           # SHOP-03, SHOP-05, BR-04, PAY-07, PAY-08
│   │   ├── controller/OrderController.java
│   │   ├── service/OrderService.java
│   │   ├── entity/Order.java, OrderDetail.java
│   │   ├── repository/OrderRepository.java, OrderDetailRepository.java
│   │   └── dto/CheckoutRequest.java, OrderResponse.java
│   ├── course/                          # SHOP-01, SHOP-09, SHOP-10
│   │   ├── controller/CourseController.java
│   │   ├── service/CourseService.java
│   │   ├── entity/Course.java, Lesson.java, CourseReview.java
│   │   └── repository/CourseRepository.java, CourseReviewRepository.java
│   ├── product/                         # SHOP-01, CRS-08 (Dụng cụ vẽ)
│   │   ├── controller/ProductController.java
│   │   ├── service/ProductService.java
│   │   ├── entity/Supply.java
│   │   └── repository/SupplyRepository.java
│   ├── payment/                         # PAY-01..11, FIN-12
│   │   ├── controller/PaymentController.java
│   │   ├── service/PaymentService.java
│   │   ├── entity/Payment.java
│   │   └── repository/PaymentRepository.java
│   ├── systemconfig/                    # ADM-02
│   │   ├── service/ConfigService.java
│   │   ├── entity/SystemConfig.java
│   │   └── repository/SystemConfigRepository.java
│   └── job/                             # PAY-08
│       └── OrderExpirationJob.java      # @Scheduled + @SchedulerLock (ShedLock)
└── resources/
    ├── application.properties           # Spring Boot configuration (profile dev/prod)
    └── db/migration/
        └── V1__be2_schema_init.sql      # Flyway initial migration script
```

---

### 2. Các công việc cần làm và Thứ tự thực hiện (sau khi được duyệt)

1. **Bước 3.1 — Cập nhật cấu hình Gradle (`build.gradle`)**:
   - Chuyển `org.springframework.boot` sang bản ổn định `3.3.4` (hỗ trợ Java 21).
   - Thêm các dependency chính:
     - `spring-boot-starter-web`
     - `spring-boot-starter-data-jpa`
     - `spring-boot-starter-validation`
     - `spring-boot-starter-security`
     - `io.jsonwebtoken:jjwt-api:0.12.6`, `jjwt-impl`, `jjwt-jackson`
     - `org.postgresql:postgresql`
     - `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`
     - `net.javacrumbs.shedlock:shedlock-spring:5.16.0`, `shedlock-provider-jdbc-template`
     - `spring-boot-starter-test`
2. **Bước 3.2 — Cập nhật `AGENTS.md`**:
   - Ghi đè mục STACK: Xác nhận chuẩn backend là **Java 21 + Spring Boot 3.3.x**, cấu trúc `com.example.art_course.*`, sử dụng `BigDecimal` cho tiền VND, Spring Data JPA / `@Transactional`, `@Lock(PESSIMISTIC_WRITE)` chống bán vượt tồn, ShedLock cho scheduled jobs.
3. **Bước 3.3 — Cập nhật các file Prompt từ 3 đến 6**:
   - Đổi các thuật ngữ kỹ thuật Node/TS (`Promise.all`, `pg client`, `express route`) sang thuật ngữ Spring Boot (`ExecutorService`, `@Transactional`, `@RestController`, JUnit 5).
4. **Bước 3.4 — Di chuyển code TypeScript vào `docs/legacy-ts/`**:
   - Di chuyển toàn bộ code TS do prompt 3 tạo ra vào thư mục `docs/legacy-ts/` để bảo toàn logic tham chiếu, xóa bỏ `package.json`, `node_modules` khỏi thư mục gốc để build Gradle thuần khiết.
5. **Bước 3.5 — Triển khai Code Java Spring Boot cho Prompt 3**:
   - `Money.java`: Tính toán VND chuẩn xác với `BigDecimal`.
   - `StateMachineValidator.java`: Bộ quy tắc chuyển trạng thái.
   - `CartController.java` & `CartService.java`: Giỏ hàng, chặn mua trùng 1 khóa cho cùng 1 Child (SHOP-04), yêu cầu Parent có Child (ACC-04).
   - `OrderController.java` & `OrderService.java`: Tách đơn theo Teacher (SHOP-05, BR-04), giữ chỗ kho trong `@Transactional` với `@Lock(PESSIMISTIC_WRITE)` (PAY-07), tính phí ship dụng cụ (#8).
   - `CourseController.java` & `CourseService.java`: Duyệt khóa học (SHOP-01), xem thử miễn phí (SHOP-10), đánh giá khóa học (SHOP-09).
   - `ProductController.java` & `ProductService.java`: Duyệt dụng cụ kèm tồn khả dụng (SHOP-01), học cụ gợi ý theo bài học.
   - `OrderExpirationJob.java`: Job hết hạn đơn sau 15 phút, trả lại tồn kho giữ chỗ (PAY-08) với `@SchedulerLock`.
6. **Bước 3.6 — Triển khai Bộ Test Bắt Buộc (JUnit 5)**:
   - Port 5 test bắt buộc sang JUnit 5:
     1. Tách đơn nhiều Teacher.
     2. Bán vượt tồn đồng thời bằng `ExecutorService` + `CountDownLatch`.
     3. Hết hạn đơn trả tồn đúng 1 lần (Idempotency).
     4. Chặn mua trùng khóa cho cùng 1 Child (SHOP-04).
     5. Child gọi API mua hàng bị 403 Forbidden (Spring Security Filter).
7. **Bước 3.7 — Kiểm tra Build & Chạy Test**:
   - Chạy kiểm tra compile Java và thực thi test suite JUnit 5.

---

### 3. Danh sách câu hỏi cần Bạn quyết định trước khi thực hiện Bước 3

1. **Môi trường JDK cục bộ**:
   - Shell hiện tại đang nhận JRE 1.8 mặc định (`C:\Program Files\Java\jre-1.8`). Để build Spring Boot 3.3.x với Java 21 (hoặc Java 17), bạn đã cài đặt JDK 21 (hoặc JDK 17) tại đường dẫn nào trên máy để cấu hình biến môi trường `JAVA_HOME`?
2. **Quy ước đặt tên Package**:
   - Giữ nguyên `com.example.art_course` như skeleton có sẵn hay bạn muốn đổi thành `com.prm.g6.artcourse`? (Đề xuất: Giữ nguyên `com.example.art_course` để khớp với `ArtCourseApplication.java` hiện có).
3. **Chuẩn hóa tên bảng chi tiết đơn hàng**:
   - Trong SQL dump là `orders_detail`, trong ERD đích là `order_details`. Đề xuất chuẩn hóa tên bảng là `order_details` (số nhiều nhất quán với `orders`), bạn có đồng ý không?

---

> [!IMPORTANT]
> **DỪNG TẠI ĐÂY THEO YÊU CẦU CỦA PROMPT.**  
> Tôi đã hoàn thành trọn vẹn **Bước 1 (Kiểm kê)** và **Bước 2 (Kế hoạch)**. Tôi tạm dừng và chờ bạn duyệt báo cáo cũng như trả lời 3 câu hỏi trên trước khi bắt đầu Bước 3 (Thực hiện).
