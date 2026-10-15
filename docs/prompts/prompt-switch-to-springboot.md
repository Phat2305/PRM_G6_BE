# Prompt — Đổi stack từ Node.js/TypeScript sang Spring Boot (Java)

## BỐI CẢNH
AGENTS.md và các prompt 1 đến 3 trước đây được viết với giả định backend là Node.js + TypeScript + Express. Giả định đó SAI: theo SRS (mục 2.2), backend là **Java / Spring Boot**. Prompt 1 (audit), 2 (migration) và 3 (commerce) đã chạy theo giả định sai. Nhiệm vụ của bạn là sửa hướng đi, không làm lại từ đầu những gì còn dùng được.

Ràng buộc giữ nguyên từ AGENTS.md: không đọc nội dung `.env`, không kết nối hay chạy migration vào database Supabase trong `DATABASE_URL`, chỉ chạy trên `DATABASE_URL_DEV` nếu có; không commit, không push; không tự tạo nhánh.

## BƯỚC 1 — Kiểm kê (chỉ đọc, chưa sửa gì)
1. Xác định hiện trạng thư mục:
   - Có `pom.xml` hoặc `build.gradle`/`build.gradle.kts` không? Nếu có: phiên bản Java, Spring Boot, ORM (JPA/Hibernate hay JDBC), công cụ migration (Flyway/Liquibase), kiểu auth, test framework, cấu trúc package.
   - Có `package.json`/`tsconfig.json`/`src/**/*.ts` không?
2. Dùng `git status` và `git diff --stat` (so với commit gần nhất) để liệt kê CHÍNH XÁC những file do prompt 1, 2, 3 tạo ra hoặc sửa, tách riêng khỏi file có sẵn từ trước của tôi. Phân loại từng file:
   - **Giữ nguyên:** tài liệu (`docs/**`, `AGENTS.md`), file SQL migration (độc lập ngôn ngữ).
   - **Chuyển sang Java:** code TypeScript do prompt 2/3 sinh ra (controller, service, validation, job, test).
   - **Không đụng:** code TypeScript có sẵn từ trước (auth, children, courses, products...). Nếu repo vốn là Node/TS và không có dự án Spring Boot nào, ĐỪNG tự quyết: ghi rõ vào báo cáo và hỏi tôi.
3. Ghi kết quả vào `docs/audit/stack-switch-report.md`, kèm bảng "file → phân loại → lý do".

## BƯỚC 2 — Kế hoạch (rồi DỪNG chờ tôi duyệt)
Trong cùng báo cáo, viết kế hoạch chuyển đổi:
- Nếu đã có dự án Spring Boot: các việc cần làm trong dự án đó (package, module, file) và thứ tự.
- Nếu chưa có: đề xuất cấu trúc dự án Spring Boot mới (thư mục, groupId/package, Java 17 hoặc 21, Spring Boot 3.x, Maven hoặc Gradle, thư viện: Spring Web, Spring Data JPA, Validation, Security, Flyway, PostgreSQL driver, ShedLock, Testcontainers) và nơi đặt nó.
- Danh sách câu hỏi cần tôi quyết định.

**DỪNG ở đây và chờ tôi trả lời. Không xóa, không di chuyển, không tạo dự án trước khi tôi duyệt.**

## BƯỚC 3 — Thực hiện (chỉ sau khi tôi duyệt)
1. **Sửa AGENTS.md:** thay mục STACK và các câu nhắc Node/TS bằng Spring Boot theo bảng ánh xạ dưới đây; giữ nguyên mọi quy tắc nghiệp vụ, quy tắc DB/bí mật và quyết định đã chốt.
2. **Viết lại `docs/prompts/prompt3-commerce.md` đến `prompt6-staff-admin-jobs.md`** theo cùng bảng ánh xạ (giữ nguyên phạm vi mã SRS, nhiệm vụ và danh sách test; chỉ đổi thuật ngữ kỹ thuật).
3. **Chuyển migration:** giữ nội dung SQL của prompt 2, đặt lại thành Flyway versioned migration (`V1__...sql`, ...) đúng thư mục của dự án Spring Boot; không chạy vào Supabase.
4. **Chuyển code prompt 3 sang Java:** cùng phạm vi, cùng hợp đồng API (đường dẫn, request/response) để app không đổi; cùng quy tắc nghiệp vụ (tách đơn theo Teacher, giữ chỗ tồn kho, hết hạn đơn, chặn mua trùng, đánh giá khóa học...).
5. **Port test bắt buộc của prompt 3** sang JUnit 5 (test bán vượt tồn bằng ExecutorService + CountDownLatch).
6. **Code TypeScript do prompt 2/3 sinh ra:** chuyển vào `docs/legacy-ts/` để tham chiếu, không xóa vĩnh viễn, và gỡ khỏi build. Code TypeScript có sẵn của tôi: không đụng.
7. Chạy build và test được phép (không cần DB thật); báo cáo kết quả.

## BẢNG ÁNH XẠ KỸ THUẬT (Node/TS → Spring Boot)
| Node/TS (đang ghi trong prompt) | Spring Boot |
|---|---|
| `src/modules/<tên>/{controller,routes,service,validation}.ts` | package `<tên>`: `@RestController`, `@Service`, `@Repository`, DTO + Jakarta Bean Validation |
| Transaction pg (BEGIN/COMMIT) | `@Transactional` |
| `SELECT ... FOR UPDATE` / `UPDATE ... WHERE stock >= n` | `@Lock(PESSIMISTIC_WRITE)` hoặc native query có điều kiện |
| Tiền: BigInt/decimal | `BigDecimal`, cột `NUMERIC(12,2)`, làm tròn ở một chỗ duy nhất |
| Advisory lock cho job | `@Scheduled` + ShedLock (JDBC) |
| `src/contracts` (interface, kiểu sự kiện) | package `contracts`: interface và `ApplicationEvent` dùng chung BE1/BE2 |
| `Promise.all` (test song song) | `ExecutorService` + `CountDownLatch` |
| jest/vitest | JUnit 5 + Mockito (+ Testcontainers PostgreSQL nếu được phép) |
| Middleware auth/validate/error | Spring Security + JWT filter, `@ControllerAdvice` xử lý lỗi |
| `DATABASE_URL_DEV` đọc từ `.env.local` | profile `dev` của Spring đọc biến môi trường `DATABASE_URL_DEV` (không commit giá trị) |
| Webhook / `PaymentGateway` interface | giữ nguyên ý: interface `PaymentGateway`, xác thực chữ ký, idempotent theo (gateway, gateway_transaction_id) |

## KẾT QUẢ
- `docs/audit/stack-switch-report.md` (Bước 1 và 2) → rồi DỪNG chờ duyệt.
- Sau khi tôi duyệt: AGENTS.md và prompt 3-6 đã cập nhật, dự án Spring Boot có migration + code prompt 3 + test, và mục "đã làm / chưa làm / giả định / câu hỏi mở".
