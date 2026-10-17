# Bằng chứng Kiểm thử Ràng buộc Cơ sở dữ liệu Thực tế (`art_course_dev` — PostgreSQL 16.15)

Tài liệu này ghi nhận kết quả thực thi trực tiếp trên database `art_course_dev` (container `pg-artcourse`, tài khoản `dev_agent`) sau khi nạp schema nền `docs/ART_COUSE.sql` (đã lọc `SET transaction_timeout` và `OWNER TO`) và chạy migration `V1__be2_schema_init.sql`.

---

## 1. Kết quả kiểm tra cấu trúc bảng (`\d`) sau khi chạy `V1__be2_schema_init.sql`

- **`public.orders`** (đã có trong nền, được bổ sung cột & ràng buộc qua `ALTER TABLE`):
  - Cột mới: `created_at timestamptz not null default CURRENT_TIMESTAMP`, `updated_at timestamptz not null default CURRENT_TIMESTAMP`.
  - Default mới: `id DEFAULT gen_random_uuid()`, `shipping_fee DEFAULT 0`, `status DEFAULT 'PENDING_PAYMENT'`.
  - Check constraints: `chk_orders_total_amount`, `chk_orders_shipping_fee`, `chk_orders_status`.
- **`public.withdrawal_requests`** (đã có trong nền, được `ALTER TABLE` và đổi kiểu `status` từ `BOOLEAN` sang `VARCHAR(20)` bằng `USING`):
  - Cột `status`: `character varying(20) not null default 'PENDING'`.
  - Cột `approved_by`: đã bỏ `NOT NULL` để cho phép tạo yêu cầu `PENDING`.
  - Cột mới: `bank_info`, `paid_by`, `rejected_reason`, `transaction_code`, `created_at`, `updated_at`.
  - Partial Unique Index: `"uq_teacher_active_withdrawal" UNIQUE, btree (teacher_id) WHERE status::text = ANY (ARRAY['PENDING'::character varying, 'APPROVED'::character varying]::text[])`.
- **`public.payments`** (bảng mới chuẩn hóa, giữ nguyên `"Payment"` của nền):
  - Partial Unique Index: `"uq_payments_gateway_tx" UNIQUE, btree (gateway, gateway_transaction_id) WHERE gateway_transaction_id IS NOT NULL`.
- **`public.order_details`** (bảng mới chuẩn hóa, giữ nguyên `orders_detail` của nền):
  - Check constraints: `chk_order_detail_type_exclusive`, `order_details_unit_price_check`, `order_details_sub_total_check`, `order_details_quantity_check`.
- **`public.money_ledger`**:
  - Trigger: `trg_prevent_money_ledger_modification BEFORE DELETE OR UPDATE ON money_ledger FOR EACH ROW EXECUTE FUNCTION fn_prevent_money_ledger_modification()`.

---

## 2. Kết quả 4 thử nghiệm bảo đảm DB bằng SQL thật

### Thử nghiệm (a): Chặn `INSERT` trùng `(gateway, gateway_transaction_id)` trên bảng `payments`

**Câu lệnh SQL thực thi:**
```sql
SAVEPOINT sp_a;
INSERT INTO public.payments (order_id, teacher_id, gateway, gateway_transaction_id, amount, status)
VALUES ('44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111102', 'VNPAY', 'TX_DUP_001', 200000, 'HELD');
INSERT INTO public.payments (order_id, teacher_id, gateway, gateway_transaction_id, amount, status)
VALUES ('44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111102', 'VNPAY', 'TX_DUP_001', 200000, 'HELD');
ROLLBACK TO SAVEPOINT sp_a;
```

**Kết quả thật từ PostgreSQL:**
```text
SAVEPOINT
INSERT 0 1
ERROR:  duplicate key value violates unique constraint "uq_payments_gateway_tx"
DETAIL:  Key (gateway, gateway_transaction_id)=(VNPAY, TX_DUP_001) already exists.
```

---

### Thử nghiệm (b): Chặn 2 yêu cầu rút tiền `PENDING` cùng một Teacher bằng Partial Unique Index

**Câu lệnh SQL thực thi:**
```sql
SAVEPOINT sp_b;
INSERT INTO public.withdrawal_requests (teacher_id, total_amount, bank_info, status)
VALUES ('11111111-1111-1111-1111-111111111102', 100000, 'VCB 001', 'PENDING');
INSERT INTO public.withdrawal_requests (teacher_id, total_amount, bank_info, status)
VALUES ('11111111-1111-1111-1111-111111111102', 150000, 'VCB 001', 'PENDING');
ROLLBACK TO SAVEPOINT sp_b;
```

**Kết quả thật từ PostgreSQL:**
```text
SAVEPOINT
INSERT 0 1
ERROR:  duplicate key value violates unique constraint "uq_teacher_active_withdrawal"
DETAIL:  Key (teacher_id)=(11111111-1111-1111-1111-111111111102) already exists.
ROLLBACK
```

---

### Thử nghiệm (c): Chặn `UPDATE` và `DELETE` trên sổ cái `money_ledger` bằng Trigger

**Câu lệnh SQL thực thi (UPDATE):**
```sql
SAVEPOINT sp_c1;
INSERT INTO public.money_ledger (id, teacher_id, entry_type, amount, note)
VALUES ('55555555-5555-5555-5555-555555555501', '11111111-1111-1111-1111-111111111102', 'HOLD', 160000, 'Test hold');
UPDATE public.money_ledger SET amount = 999999 WHERE id = '55555555-5555-5555-5555-555555555501';
ROLLBACK TO SAVEPOINT sp_c1;
```

**Kết quả thật từ PostgreSQL (UPDATE):**
```text
SAVEPOINT
INSERT 0 1
ERROR:  BẢO MẬT: Bảng money_ledger là sổ cái chỉ-ghi-thêm (append-only). Tuyệt đối cấm UPDATE hoặc DELETE!
CONTEXT:  PL/pgSQL function fn_prevent_money_ledger_modification() line 3 at RAISE
ROLLBACK
```

**Câu lệnh SQL thực thi (DELETE):**
```sql
SAVEPOINT sp_c2;
INSERT INTO public.money_ledger (id, teacher_id, entry_type, amount, note)
VALUES ('55555555-5555-5555-5555-555555555501', '11111111-1111-1111-1111-111111111102', 'HOLD', 160000, 'Test hold');
DELETE FROM public.money_ledger WHERE id = '55555555-5555-5555-5555-555555555501';
ROLLBACK TO SAVEPOINT sp_c2;
```

**Kết quả thật từ PostgreSQL (DELETE):**
```text
SAVEPOINT
INSERT 0 1
ERROR:  BẢO MẬT: Bảng money_ledger là sổ cái chỉ-ghi-thêm (append-only). Tuyệt đối cấm UPDATE hoặc DELETE!
CONTEXT:  PL/pgSQL function fn_prevent_money_ledger_modification() line 3 at RAISE
ROLLBACK
```

---

### Thử nghiệm (d): Ràng buộc `CHECK` của `order_details` chặn dòng có cả `course_id` lẫn `supply_id` hoặc giá âm

**Câu lệnh SQL thực thi (d1 — cả `course_id` lẫn `supply_id` khác NULL):**
```sql
SAVEPOINT sp_d1;
INSERT INTO public.order_details (order_id, product_type, course_id, supply_id, child_id, product_name, unit_price, quantity, sub_total)
VALUES ('44444444-4444-4444-4444-444444444401', 'COURSE', '22222222-2222-2222-2222-222222222201', '33333333-3333-3333-3333-333333333301', '11111111-1111-1111-1111-111111111103', 'Loi ca hai ID', 200000, 1, 200000);
ROLLBACK TO SAVEPOINT sp_d1;
```

**Kết quả thật từ PostgreSQL (d1):**
```text
SAVEPOINT
ERROR:  new row for relation "order_details" violates check constraint "chk_order_detail_type_exclusive"
DETAIL:  Failing row contains (2ccb8cb1-0cda-4f7a-8c40-bbd51552c380, 44444444-4444-4444-4444-444444444401, COURSE, 22222222-2222-2222-2222-222222222201, 33333333-3333-3333-3333-333333333301, null, 11111111-1111-1111-1111-111111111103, Loi ca hai ID, 200000, 1, 200000, 0.0000, 0, 0, null, null, null, null, null, null, 2026-10-17 05:28:01.610472+00, 2026-10-17 05:28:01.610472+00).
ROLLBACK
```

**Câu lệnh SQL thực thi (d2 — `unit_price` âm):**
```sql
SAVEPOINT sp_d2;
INSERT INTO public.order_details (order_id, product_type, course_id, child_id, product_name, unit_price, quantity, sub_total)
VALUES ('44444444-4444-4444-4444-444444444401', 'COURSE', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111103', 'Gia am', -50000, 1, 200000);
ROLLBACK TO SAVEPOINT sp_d2;
```

**Kết quả thật từ PostgreSQL (d2):**
```text
SAVEPOINT
ERROR:  new row for relation "order_details" violates check constraint "order_details_unit_price_check"
DETAIL:  Failing row contains (9d073481-7c81-4e13-9883-66e92e2cb095, 44444444-4444-4444-4444-444444444401, COURSE, 22222222-2222-2222-2222-222222222201, null, null, 11111111-1111-1111-1111-111111111103, Gia am, -50000, 1, 200000, 0.0000, 0, 0, null, null, null, null, null, null, 2026-10-17 05:28:01.610472+00, 2026-10-17 05:28:01.610472+00).
ROLLBACK
```


---

## 3. Bằng chứng khởi chạy ứng dụng với Profile `dev` (Flyway Baseline + V1, Hibernate Validate, Swagger)

**Kết quả bảng `public.flyway_schema_history` sau khi chạy ứng dụng với `--spring.profiles.active=dev`:**
```text
 installed_rank | version |      description      |   type   |         script          | success 
----------------+---------+-----------------------+----------+-------------------------+---------
              1 | 0       | << Flyway Baseline >> | BASELINE | << Flyway Baseline >>   | t
              2 | 1       | be2 schema init       | SQL      | V1__be2_schema_init.sql | t
(2 rows)
```

**Trích log khởi động & kiểm tra `/swagger/index.html`:**
```text
SWAGGER_HTTP_STATUS: 200
INFO c.e.art_course.config.FlywayConfig       : [FLYWAY] Đang khởi chạy Flyway migration trên profile dev cho database hợp lệ 'art_course_dev'...
INFO org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5432/art_course_dev (PostgreSQL 16.15)
INFO o.f.c.i.s.JdbcTableSchemaHistory         : Creating Schema History table "public"."flyway_schema_history" with baseline ...
INFO c.e.art_course.config.FlywayConfig       : [FLYWAY] Flyway migration cho 'art_course_dev' đã hoàn tất.
INFO org.hibernate.orm.core                   : HHH000001: Hibernate ORM core version 7.4.5.Final
INFO c.e.art_course.ArtCourseApplication      : Started ArtCourseApplication in 10.266 seconds (process running for 10.631)
```


---

## 4. Kết quả kiểm thử `V2__ledger_hardening.sql` trên `art_course_dev` (PostgreSQL 16.15)

### 4.1. Thực thi `V2__ledger_hardening.sql` (`psql -v ON_ERROR_STOP=1`)
```text
CREATE FUNCTION
psql:/tmp/V2__ledger_hardening.sql:13: NOTICE:  trigger "trg_prevent_money_ledger_truncate" for relation "public.money_ledger" does not exist, skipping
DROP TRIGGER
CREATE TRIGGER
psql:/tmp/V2__ledger_hardening.sql:19: NOTICE:  constraint "chk_order_details_subtotal_math" of relation "order_details" does not exist, skipping
ALTER TABLE
ALTER TABLE
```

### 4.2. Chứng minh SQL thật: Chặn `TRUNCATE` trên `money_ledger` và chặn dòng `sub_total != unit_price * quantity` trên `order_details`
```text
SAVEPOINT sp_trunc;
SAVEPOINT
TRUNCATE TABLE public.money_ledger;
ERROR:  BẢO MẬT: Bảng money_ledger là sổ cái chỉ-ghi-thêm (append-only). Tuyệt đối cấm UPDATE, DELETE hoặc TRUNCATE! (Operation: TRUNCATE)
CONTEXT:  PL/pgSQL function fn_prevent_money_ledger_modification() line 3 at RAISE
ROLLBACK TO SAVEPOINT sp_trunc;
ROLLBACK
SAVEPOINT sp_sub;
SAVEPOINT
INSERT INTO public.order_details (order_id, product_type, supply_id, product_name, unit_price, quantity, sub_total, delivery_status) VALUES ('44444444-4444-4444-4444-444444444401', 'SUPPLY', '33333333-3333-3333-3333-333333333301', 'Co ve', 50000, 2, 90000, 'PENDING');
ERROR:  new row for relation "order_details" violates check constraint "chk_order_details_subtotal_math"
DETAIL:  Failing row contains (8efdacd1-dbd1-4107-a786-91e8e81e64dd, 44444444-4444-4444-4444-444444444401, SUPPLY, null, 33333333-3333-3333-3333-333333333301, null, null, Co ve, 50000, 2, 90000, 0.0000, 0, 0, PENDING, null, null, null, null, null, 2026-10-17 05:51:34.838919+00, 2026-10-17 05:51:34.838919+00).
ROLLBACK TO SAVEPOINT sp_sub;
ROLLBACK
```

> **Ghi chú bảo mật triển khai (Production Hardening):**
> Khi triển khai thật, ứng dụng (app) bắt buộc phải chạy bằng database role **không sở hữu bảng** (non-owner role) và chỉ được cấp quyền `SELECT, INSERT` trên bảng `public.money_ledger` (thu hồi `UPDATE, DELETE, TRUNCATE, TRIGGER`). Tài khoản sở hữu bảng/DB (table owner hoặc superuser) có thể thực thi `ALTER TABLE public.money_ledger DISABLE TRIGGER ALL` để tắt trigger, do đó phân tách role ứng dụng và role sở hữu schema là bắt buộc để bảo vệ tính bất biến của sổ cái.

---

## 5. Chưa chứng minh trên DB thật (tại thời điểm chỉ chạy SQL thuần) & Trạng thái sau khi chạy Testcontainers `postgres:16-alpine`

1. **Logic Java bắt lỗi trùng (`gateway, gateway_transaction_id`)**:
   - *Ở bước SQL thuần*: Chưa chứng minh qua tầng Java `PaymentService`.
   - *Sau bước JUnit Testcontainers (`postgres:16-alpine`)*: Đã kiểm chứng qua `PaymentFulfillmentTests.testWebhook_ConcurrentSameGatewayTransactionId_OnRealPostgres` (PASS).
2. **Khóa dòng khi tranh chấp (`@Lock(PESSIMISTIC_WRITE)`)**:
   - *Ở bước SQL thuần*: Chưa chứng minh qua tầng JPA.
   - *Sau bước JUnit Testcontainers (`postgres:16-alpine`)*: Đã kiểm chứng qua `CommerceTests.testConcurrentOversellingPrevention` (10 luồng mua đồng thời tồn kho 5 -> đúng 5 đơn thành công, 5 đơn `INSUFFICIENT_STOCK`) và khóa dòng payment khi 2 luồng webhook chạy đồng thời (PASS).
3. **Rollback khi lỗi giữa transaction**:
   - *Ở bước SQL thuần*: Chưa chứng minh qua `@Transactional` của Spring.
   - *Sau bước JUnit Testcontainers (`postgres:16-alpine`)*: Đã kiểm chứng qua `PaymentFulfillmentTests.testWebhook_RollbackAllOnMidTransactionError_OnRealPostgres` (PASS).
4. **3 test `@Disabled` (`testConcurrentOversellingPrevention`, `testOrderExpirationIdempotency`, `testPreventDuplicateCourseForChild`) + test trigger `money_ledger`**:
   - *Ở bước SQL thuần*: Đang để `@Disabled`.
   - *Sau bước JUnit Testcontainers (`postgres:16-alpine`)*: Đã bỏ `@Disabled` toàn bộ và chạy PASS trên PostgreSQL 16 thật.
5. **Phân quyền qua Spring Security thật (`SecurityFilterChain` + `JwtAuthFilter`)**:
   - *Ở bước SQL thuần*: Mới dùng standalone MockMvc.
   - *Sau bước JUnit*: Đã kiểm chứng qua `OrderSecurityWebMvcTest` tích hợp `SecurityConfig` + `JwtAuthFilter` + `springSecurity()` (8/8 test PASS).
6. **VNPay / MoMo Sandbox thật**: **CHƯA CHỨNG MINH** (hiện kiểm thử qua `MockPaymentGateway` và chữ ký HMAC nội bộ, chưa gọi HTTP thực tế ra môi trường mạng VNPay/MoMo Sandbox).
7. **Tích hợp runtime với code BE1 thật**: **CHƯA CHỨNG MINH** (`IEnrollmentContract`, `IVideoAccessContract` hiện vẫn qua stub/contract adapter chờ BE1 ghép code).
