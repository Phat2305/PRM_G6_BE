# Rà soát luồng "Khóa học theo yêu cầu" (Customize): chỗ thiếu và lộ trình

_Ngày rà soát: 2026-10-25. Rà bằng cách đọc code, chưa chạy thử luồng trên server._

## Context
Câu hỏi: luồng sau đang thiếu gì ở cả FE (PRM393_G6_FE) lẫn BE (PRM_G6_BE)? Phụ huynh đăng yêu cầu → Giáo viên chat thương lượng giá → Phụ huynh chấp nhận → Giáo viên làm khóa và đăng → Staff duyệt → Phụ huynh nhận thông báo hoàn thành.
Phạm vi đã chốt: **chỉ báo cáo kèm lộ trình, chưa sửa code**. Lộ trình giả định **thanh toán PayOS thật** và **trạng thái theo SRS**.
Nguồn: hai lượt rà code chỉ đọc (FE `PRM393_G6_FE`, BE `PRM_G6_BE`), mã yêu cầu CUS-01..21 và §8.1 trong `docs/SRS_v3.pdf`, `docs/handoff-be1.md`. Hai lỗi nặng nhất mình đã tự mở code kiểm tra lại (đánh dấu ✔).

**Kết luận:** luồng chỉ chạy thật tới bước "giáo viên gửi báo giá", và chỉ ở phía Giáo viên. Từ lúc Phụ huynh chấp nhận trở đi thì cả BE lẫn FE đều không có gì nối tiếp: không đổi trạng thái, không tạo đơn, không thanh toán, không gắn khóa học với yêu cầu, không có bước Staff duyệt, không gửi thông báo, không cho bé vào học, không giải ngân. Phía BE đã có sẵn vài mảnh của phần mua bán (dòng đơn `CUSTOMIZE`, nhánh giải ngân, job hết hạn 24h) nhưng chưa có gì gọi tới chúng.

## Chỗ thiếu theo từng bước

| # | Bước | BE | FE |
|---|---|---|---|
| 1 | Phụ huynh đăng yêu cầu | Đã có `POST /api/customize-requests` (lưu OPEN), GET/PUT/DELETE. **Thiếu:** bộ trạng thái chỉ có OPEN/ACCEPTED/REJECTED/CANCELLED. `PATCH /{id}/status` cho **mọi** Teacher/Admin đặt trạng thái bất kỳ, kể cả trên yêu cầu của phụ huynh khác, không kiểm tra bước chuyển (`CustomizeRequestService.updateStatus`). | **Giả lập:** `RequestFormScreen._submit` chỉ thêm vào `mockRequests` (`request_form_screen.dart:40-72`). `MyRequestsScreen` đọc mock, không gọi `GET …/parent/{id}`. FE dùng trạng thái `PENDING_REVIEW`, `AWAITING_PAYMENT` mà BE không có. |
| 2 | Chat thương lượng | Đã có `POST /api/conversations` (context tự do), `GET /context`, `POST /api/messages`. **Thiếu:** không kiểm tra `contextId` là yêu cầu OPEN có thật, cũng không kiểm tra `parentId` có phải chủ yêu cầu. | Giáo viên chat thật (`conversationForRequest`, `syncTeacherConversations`). **Phụ huynh không có hội thoại thật:** không có hàm sync phía phụ huynh, `InboxScreen` đọc mock, `ConversationScreen` hiện tin mẫu khi `id == null`. |
| 3 | Chấp nhận báo giá + thanh toán | Đã có `POST /api/offers` và `PATCH /api/offers/{id}/status`. ✔ **Khi chấp nhận chỉ lưu báo giá** (`OfferService.java:199`): yêu cầu vẫn OPEN, các báo giá khác không bị thay thế (chấp nhận được nhiều báo giá cùng lúc), không phát event `customize_offer_accepted` (CUS-08), không tạo đơn hay thanh toán. Checkout không có trường `customizeRequestId`/`offerId`. `CustomizeQuoteExpirationJob` gán cứng 24h và không trả yêu cầu/báo giá về trạng thái cũ. | **Giả lập:** nút Chấp nhận trong chat (`conversation_screen.dart:118-129`) và ở chi tiết yêu cầu (`request_detail_screen.dart:81-96`) chỉ đổi trạng thái cục bộ. `Offer` chưa có `id`. Thanh toán dùng `showMockGateway` với đơn giả `YC-0001` (`request_detail_screen.dart:145-166`), không qua PayOS. |
| 4 | Giáo viên làm và đăng khóa | `POST/PUT /api/courses` nhận `customizeRequestId` nhưng không kiểm tra gì. ✔ **Giáo viên tự gán `status`, đặt thẳng PUBLISHED để bỏ qua bước duyệt** (`CourseService.java:89,139`). Tạo khóa không kiểm tra role TEACHER. Chưa có endpoint "Bàn giao" (CUS-11). Khóa riêng vẫn hiện ở danh mục công khai (CUS-10). | `CourseEditScreen` không biết khóa này thuộc yêu cầu nào và không gửi `customizeRequestId`. Nút "Bàn giao" chỉ có với dữ liệu mẫu (`request_board_screen.dart:203-216`). |
| 5 | Staff duyệt | Chỉ có endpoint ghi nhật ký `moderation_reviews`, và bị trùng hai chỗ (`/api/v1/moderation/reviews` và `/api/moderation-reviews`). **Không có endpoint duyệt khóa học** (PENDING_REVIEW → PUBLISHED/FAILED). Code khóa học chưa gọi `IModerationContract`. | **Giả lập hoàn toàn:** `ModerationScreen` dùng `_queueSeed` cứng, các thư mục `staff/` không gọi API nào. |
| 6 | Thông báo hoàn thành + bé được học + giải ngân | `NotificationService` không có hàm nào cho customize. Không phát event `customize_completed` (CUS-12). Không có endpoint phụ huynh xác nhận hoàn thành. Dòng đơn CUSTOMIZE có `course_id` NULL nên `grantAccess` không chạy được, bé không được vào học. Mốc tính X ngày giải ngân bắt đầu từ lúc tạo đơn thay vì lúc bàn giao; `customize_auto_confirm_days` để trống nên không bao giờ giải ngân. | **Giả lập/thiếu:** "Gán cho con & hoàn tất", "Yêu cầu sửa", "Khiếu nại" chỉ đổi trạng thái cục bộ. Chưa có màn hay API thông báo (chuông chỉ là toast). Không có enrollment nên bé không thấy khóa. |

**Lỗi bảo mật nên sửa trước tiên** (độc lập với lộ trình):
1. Giáo viên tự đặt khóa học `PUBLISHED`, bỏ qua Staff duyệt (`CourseService` create/update).
2. Bất kỳ Teacher nào cũng đổi được trạng thái yêu cầu của người khác (`CustomizeRequestService.updateStatus`).
3. Phụ huynh chấp nhận được nhiều báo giá cho cùng một yêu cầu.

## Lộ trình đề xuất (PayOS thật, trạng thái SRS)

**Trạng thái yêu cầu:** `OPEN → ACCEPTED → FUNDED → DELIVERED → COMPLETED`, thêm nhánh `CANCELLED`.
- `ACCEPTED` nghĩa là đã chấp nhận báo giá và đang chờ thanh toán.
- `FUNDED` là trạng thái duy nhất thêm vào so với bộ đã chọn: có PayOS thì phải tách "đã chấp nhận" với "đã trả tiền".

**Trạng thái báo giá:** giữ chữ thường đang dùng, thêm `superseded`.

Mỗi giai đoạn dùng được ngay khi xong:

**GĐ0: Bịt lỗi bảo mật** (BE, ~nửa ngày)
- `CourseService`: chỉ TEACHER được tạo khóa. Giáo viên chỉ được đặt `DRAFT`/`PENDING_REVIEW`; `PUBLISHED`/`FAILED` chỉ Staff/Admin đặt.
- `CustomizeRequestService.updateStatus`: kiểm tra bước chuyển hợp lệ và quyền theo từng bước.
- `OfferService.updateStatus`, khi phụ huynh chấp nhận: yêu cầu → `ACCEPTED`, báo giá khác của yêu cầu đó → `superseded`, chặn chấp nhận thêm khi yêu cầu không còn OPEN.

**GĐ1: Phụ huynh chạy bằng dữ liệu thật** (FE, ~1 ngày)
> **Đã làm (25/10):** đăng/nạp yêu cầu của phụ huynh, nạp báo giá thật, chấp nhận báo giá (BE: yêu cầu → ACCEPTED, từ chối báo giá khác, chặn chấp nhận lần hai). **Còn lại:** hội thoại phía phụ huynh, sửa/đóng/xóa yêu cầu qua API.
- `ShopApi`: `createRequest`, `parentRequests` (`GET …/parent/{id}`), `parentConversations`, `acceptOffer` (`PATCH /api/offers/{id}/status`), và hàm sync phía phụ huynh thêm vào `syncParentData`.
- Map trạng thái BE sang `requestSteps` trong `request_detail_screen.dart`. Đây là nguồn thứ tự duy nhất, phải sửa cả hàm phân nhóm ở `my_requests_screen.dart:10-16`.
- `Offer` thêm `id`; chi tiết yêu cầu đọc báo giá thật qua `GET /api/offers/request/{id}`.

**GĐ2: Thanh toán PayOS** (BE + FE, ~1,5–2 ngày)
> **Đã làm (25/10):** `POST /api/v1/orders/customize/{requestId}` (`CustomizeOrderService`) tạo hoặc dùng lại đơn `CUSTOMIZE` theo giá báo giá, hạn `quote_payment_timeout_hours` (mặc định 24h); hoa hồng theo tỷ lệ khóa học; thanh toán PayOS thành công thì yêu cầu → FUNDED; FE thanh toán qua `activeGateway`. **Còn lại:** đơn hết hạn chưa trả yêu cầu về OPEN (CUS-18), chưa có event `customize_offer_accepted`.
- BE: lắng nghe event `customize_offer_accepted` rồi tạo đơn có dòng `CUSTOMIZE` (`customize_request_id`, giá theo báo giá), dùng lại `OrderService` và `PaymentService`. Thanh toán thành công thì yêu cầu → `FUNDED`.
- Job hết hạn đọc `quote_payment_timeout_hours`; hết hạn thì trả yêu cầu về `OPEN` và báo giá về `pending`/`expired`.
- FE: `_pay` dùng `ShopApi.createPayment` + `activeGateway` như checkout dụng cụ, bỏ `showMockGateway`.

**GĐ3: Giáo viên làm khóa và Staff duyệt** (BE + FE, ~1,5 ngày)
- BE: tạo khóa có `customizeRequestId` thì kiểm tra yêu cầu đang `FUNDED` và giáo viên này có báo giá được chấp nhận. Khóa đó ẩn khỏi danh mục công khai.
- BE: endpoint Staff duyệt khóa (`PENDING_REVIEW → PUBLISHED/FAILED`), gọi `IModerationContract` để ghi nhật ký. Khóa customize được duyệt thì yêu cầu → `DELIVERED`.
- FE: từ Bảng yêu cầu (tab Đang làm) mở `CourseEditScreen(customizeRequestId: …)`. `ModerationScreen` đọc hàng chờ thật và gọi API duyệt.

**GĐ4: Hoàn thành, thông báo, bé được học, giải ngân** (BE + FE, ~1 ngày)
- BE: khi yêu cầu → `DELIVERED`: gắn `course_id` vào dòng đơn, `grantAccess` cho bé, gửi thông báo cho phụ huynh qua `NotificationService`.
- BE: endpoint phụ huynh xác nhận → `COMPLETED`, phát `customize_completed`, dòng đơn `delivery_status = COMPLETED` để `EscrowReleaseService` giải ngân. Mốc X ngày tính từ lúc bàn giao.
- FE: API danh sách thông báo + màn thông báo đơn giản (thay toast ở chuông). Nút "Gán cho con & hoàn tất" gọi endpoint xác nhận.

**Để sau:** sửa bài (REVISION_REQUESTED), khiếu nại/hoàn tiền (CUS-13/19/20), Staff duyệt bài đăng trước khi lên bảng (PENDING_REVIEW, CUS-03), báo giá hết hạn tự động phía FE.

## Kiểm tra khi triển khai
- Mỗi giai đoạn có unit test BE cho bước chuyển trạng thái (theo kiểu `CustomizeRequestStatusTest`, `UserAccountTest`) và chạy `gradlew test`.
- FE: thêm luồng vào `test/widget_test.dart`, chạy `flutter analyze` và `flutter test`.
- End-to-end trên BE local (`dev` profile, `art_course_dev`) với 3 tài khoản parent/teacher/staff:
  1. Tạo yêu cầu, chat, gửi báo giá, chấp nhận.
  2. Thanh toán PayOS sandbox; yêu cầu thành FUNDED.
  3. Giáo viên làm khóa; Staff duyệt.
  4. Phụ huynh nhận thông báo; bé thấy khóa; phụ huynh xác nhận rồi giải ngân.

**Chưa kiểm tra:** chưa chạy app hay gọi API thật cho luồng này; phần `PaymentService` và module video/lesson mới đọc lướt. Thời gian ước lượng là thô, giả định một người quen codebase.

## Trạng thái ngày 25/10 (sau GĐ1, GĐ2 và chat GĐ A–D)

Kiểm chứng: E2E 40/40 điểm trên BE chạy thật + Postgres tạm (chi tiết trong `docs/chat-offer-plan.md`, GĐ D); test BE 143 (7 test Testcontainers hỏng sẵn vì thư mục migration bị xóa ở commit `9194e89`); test FE 184/184.

| Bước | Trạng thái |
|---|---|
| 1. Phụ huynh đăng yêu cầu | **Xong** (BE + FE; banner trang chủ, nút "+" tab Không gian, danh sách thật) |
| 2. Chat thương lượng | **Xong** (hộp thư `/me` cả hai vai trò, chưa đọc, tin mới 5 giây/lần, thông báo NEW_MESSAGE) |
| 3a. Báo giá trong chat | **Xong** (một transaction tạo báo giá + tin, thay báo giá cũ, thông báo NEW_OFFER) |
| 3b. Chấp nhận báo giá | **Xong** (chat hoặc màn chi tiết; yêu cầu ACCEPTED, từ chối báo giá khác, chặn chấp nhận lần hai, OFFER_ACCEPTED) |
| 3c. Thanh toán | **Xong** (đơn CUSTOMIZE theo giá báo giá, hạn 24h, hoa hồng như khóa học, PayOS, xong thì FUNDED). Chưa chạy thật với PayOS sandbox |
| 4. Giáo viên làm khóa và bàn giao | **Xong (25/10, tối)**: khóa gắn `customizeRequestId` chỉ khi yêu cầu FUNDED và đúng giáo viên có báo giá được chấp nhận; `POST /api/customize-requests/{id}/deliver` (khóa ≥ 1 bài) → DELIVERED, khóa PUBLISHED nhưng ẩn khỏi danh mục công khai (CUS-10); thông báo CUSTOMIZE_DELIVERED |
| 5. Staff duyệt khóa | **Thiếu** (khóa riêng được công khai cho bé ngay khi bàn giao, chưa qua Staff) |
| 6. Thông báo hoàn thành, bé được học, giải ngân | **Xong (25/10, tối)**: `GET .../delivery` + `POST .../complete` (bé của chính phụ huynh) → cấp `child_enroll`, dòng đơn COMPLETED, `tryReleaseEscrow` (payment RELEASED, sổ cái RELEASE), yêu cầu COMPLETED, thông báo CUSTOMIZE_COMPLETED. Sửa lỗi: bật `customize_auto_confirm_days` thì trước đây xác nhận của phụ huynh bị bỏ qua. FE: `CustomizeDeliveryScreen`, `NotificationsScreen` + nút chuông. E2E 62/62 |

Còn mở từ GĐ0: giáo viên vẫn tự đặt `status` khóa học (bỏ qua duyệt); `PATCH /api/customize-requests/{id}/status` vẫn cho mọi Teacher/Admin đổi trạng thái bất kỳ.
