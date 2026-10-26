# Kế hoạch: hoàn thiện hội thoại Teacher – Phụ huynh và báo giá trong chat

_Lập ngày 2026-10-25 từ việc đọc code FE (PRM393_G6_FE) và BE (PRM_G6_BE) cùng schema Supabase. Chưa sửa code._
_Liên quan: `docs/customize-flow-review.md` (GĐ1–GĐ2 đã xong: phụ huynh đăng yêu cầu, chấp nhận báo giá ở màn chi tiết, thanh toán PayOS)._

## Mục tiêu
1. Giáo viên nhắn tin cho phụ huynh về một yêu cầu customize.
2. Hai bên trao đổi qua lại.
3. Giáo viên gửi báo giá ngay trong chat (giá + số ngày bàn giao), có thể gửi báo giá mới thay báo giá cũ.
4. Phụ huynh chấp nhận báo giá ngay trong chat rồi đi tới thanh toán.

Cả hai phía đều thấy tin mới, tin chưa đọc và trạng thái từng báo giá đúng với BE.

## Hiện trạng

| Phần | BE | FE |
|---|---|---|
| Tạo hội thoại | Có `POST /api/conversations`. **Không kiểm tra** `contextId` là yêu cầu có thật, `parentId` có phải chủ yêu cầu. **Không chặn trùng**: một giáo viên có thể tạo nhiều hội thoại cho cùng một yêu cầu. | Giáo viên tạo từ Bảng yêu cầu (`conversationForRequest`, tự tìm hội thoại cũ trước). Phụ huynh không tạo, chỉ trả lời. |
| Danh sách hội thoại | `GET /api/conversations/parent/{id}` và `/teacher/{id}` chỉ trả id. Không có tên hai bên, tiêu đề yêu cầu, tin cuối, số chưa đọc. | Giáo viên: `syncTeacherConversations` gọi thêm 2 request cho **mỗi** hội thoại để lấy tiêu đề và tin cuối (N+1). **Phụ huynh: hoàn toàn dữ liệu mẫu**, không có hàm sync. Xóa/ghim/lưu trữ chỉ là SnackBar. |
| Tin nhắn | Đọc/gửi được (`/api/messages`). Có cờ `is_offer` nhưng **không gắn với báo giá nào**. Không có trạng thái đã đọc. Không giới hạn độ dài. | Đọc/gửi thật khi hội thoại có `id`. Không tự cập nhật tin mới, phải mở lại màn. Tên bên kia đang gán cứng (`mockTeacher`, "Phụ huynh xxxx"). |
| Gửi báo giá | `POST /api/offers` (giá + lời nhắn). Số ngày bàn giao chỉ nằm trong chữ của lời nhắn. Báo giá cũ đang chờ của cùng giáo viên **không bị thay thế**, nên còn nhiều báo giá "pending" cùng lúc. | `_sendOffer` gọi **hai API riêng**: tạo báo giá rồi gửi tin `isOffer`. Lỗi ở giữa thì có báo giá mà không có tin, hoặc ngược lại. |
| Trạng thái báo giá trong chat | (không có liên kết tin ↔ báo giá) | Đoán: tin `isOffer` cuối là báo giá hiện hành. Trạng thái lấy từ `latestOfferStatus`, tức **báo giá cuối của cả yêu cầu, bất kể giáo viên nào**. Giáo viên khác được chấp nhận thì chat này cũng hiện "Đã chấp nhận" (**lỗi**). |
| Chấp nhận trong chat | `PATCH /api/offers/{id}/status` đã đúng (yêu cầu → ACCEPTED, từ chối báo giá khác). | Nút "Chấp nhận" trong chat **chỉ đổi trạng thái trên máy**, vì tin không có id báo giá. |
| Thông báo | Bảng `notifications` có sẵn (`recipient_id`, `type`, `reference_id`, `is_read`) nhưng không có loại nào cho chat hay báo giá. | Không có màn thông báo. Chuông chỉ là toast. |

Schema hiện tại: `conversations(id, parent_id, teacher_id, lesson_id, context_type, context_id)`, `messages(id, conversation_id, sender_id, content, is_offer, created_at)`, `offers(id, request_id, teacher_id, price, message, status)`.

## Quyết định cần chốt trước khi làm
1. **Thêm cột vào DB.** Đề xuất làm. BE đang để `ddl-auto=none` nên phải chạy SQL tay trên Supabase; mình sẽ xuất file SQL để bạn tự chạy. Cần thêm:
   - `messages.offer_id uuid null` (FK `offers.id`): gắn tin báo giá với báo giá;
   - `offers.delivery_days int null`: số ngày bàn giao, thay vì để trong chữ;
   - `conversations.parent_last_read_at`, `conversations.teacher_last_read_at` (`timestamptz null`): tính tin chưa đọc.

   Không thêm cột thì chỉ làm được GĐ A một phần (gộp tạo báo giá + tin, thay báo giá cũ), và trạng thái báo giá trong chat vẫn phải đoán.
2. **Tin mới đến bằng cách nào.** Đề xuất: **hỏi lại server mỗi 5 giây khi đang mở chat, mỗi 30 giây ở hộp thư**. WebSocket/STOMP để sau, vì phải thêm hạ tầng và đổi cấu hình deploy.
3. **Phụ huynh có được mở hội thoại trước không.** Đề xuất: không. Theo luồng CUS-04 thì giáo viên liên hệ trước. Phụ huynh vào chat từ hộp thư, hoặc từ thẻ báo giá ở màn chi tiết yêu cầu.

## Lộ trình (mỗi giai đoạn dùng được ngay khi xong)

### GĐ A: Báo giá gắn với tin nhắn (BE ~1 ngày, FE ~0,5 ngày)
> **Đã làm (25/10):** SQL `docs/sql/2026-10-25_chat_offer.sql` đã chạy trên Supabase. BE: `POST /api/offers` nhận `conversationId`/`deliveryDays`, thay báo giá cũ (`superseded`), tạo tin báo giá cùng transaction; tin nhắn trả kèm trạng thái báo giá (đọc theo lô); `/api/messages` bỏ qua cờ `isOffer`, giới hạn 2000 ký tự; tạo hội thoại kiểm tra yêu cầu/chủ yêu cầu và trả hội thoại cũ nếu đã có. Test: `ChatOfferTest`. FE: gửi báo giá một lần gọi, trạng thái từng báo giá từ BE, chấp nhận trong chat gọi API, bỏ `latestOfferStatus`.
**BE**
- `POST /api/offers` nhận thêm `conversationId` và `deliveryDays`. Trong **một transaction**:
  - kiểm tra giáo viên thuộc hội thoại, hội thoại đúng yêu cầu, yêu cầu đang OPEN;
  - đổi báo giá `pending` cũ của **cùng giáo viên, cùng yêu cầu** sang `superseded` (thêm vào trạng thái hợp lệ);
  - tạo báo giá;
  - tạo tin `is_offer = true, offer_id = …`.

  Trả về cả báo giá lẫn tin.
- `MessageResponse` thêm `offerId`, `offerStatus`, `offerPrice`, `deliveryDays` (đọc kèm theo lô, không N+1).
- `ConversationService.create`:
  - với `CUSTOMIZE_REQUEST`: yêu cầu phải tồn tại và OPEN, `parentId` phải là chủ yêu cầu;
  - đã có hội thoại (yêu cầu, giáo viên) thì trả hội thoại cũ, không tạo mới.
- Tin nhắn: tối đa 2000 ký tự, chặn tin rỗng (đã có).
- Test: thay báo giá cũ, chặn sai hội thoại/yêu cầu, không tạo hội thoại trùng, tin có `offerId`.

**FE**
- `_sendOffer` gọi một API duy nhất (`ShopApi.sendOffer(conversationId, requestId, price, days)`).
- `_loadRemote` đọc `offerStatus` của từng tin: pending → SENT, accepted → ACCEPTED, superseded/rejected → SUPERSEDED/DECLINED. **Bỏ `latestOfferStatus`**, vì đó là nguồn của lỗi lẫn giáo viên.
- Form báo giá chặn giá ≤ 0 và số ngày ngoài 1–60.

### GĐ B: Phụ huynh chat thật (FE ~1 ngày, BE nhỏ)
> **Đã làm (25/10):** BE `GET /api/conversations/me` (`InboxController`/`InboxService`, SQL một câu trong `ConversationSummaryRepository`, đã chạy thử trên bản sao schema) và `PATCH /api/conversations/{id}/read`; test `InboxServiceTest`. FE: hộp thư Parent/Teacher từ `/me` (tên thật, số chưa đọc), kéo để làm mới, badge tab "Tin nhắn", mở chat là đánh dấu đã đọc, nút "Thanh toán" sau khi chấp nhận trong chat, nút "Nhắn tin" trên thẻ báo giá; bỏ các thao tác giả xóa/ghim. Kèm sửa: nút "+" tab Không gian nay đăng yêu cầu thật (`postCustomRequest`), thẻ báo giá không còn lặp chữ "Bàn giao trong".
**BE**
- `GET /api/conversations/me`: danh sách hội thoại của người đang đăng nhập, mỗi dòng có `parentName`, `teacherName`, `contextTitle`, `lastMessage`, `lastMessageAt`, `unreadCount`, sắp theo tin mới nhất. Một query gộp, thay cho N+1 phía FE.
- `PATCH /api/conversations/{id}/read`: ghi `*_last_read_at` của người gọi.

**FE**
- `ShopApi.syncConversations()` dùng `/me` cho **cả hai vai trò**. Thêm vào `syncParentData`, thay `syncTeacherConversations`. Mapper lấy tên thật, không gán cứng `mockTeacher`.
- `ConversationScreen`:
  - xác định "tin của mình" bằng `senderId == userId`, không suy từ vai trò;
  - mở màn thì gọi `read`;
  - nút **Chấp nhận** trên thẻ báo giá gọi `ShopApi.acceptOffer(offerId)`;
  - thành công thì thẻ hiện "Đã chấp nhận" kèm nút **Thanh toán**, mở `RequestDetailScreen` của yêu cầu đó (dùng lại `_pay` của GĐ2).
- Màn chi tiết yêu cầu: mỗi thẻ báo giá thêm nút **Nhắn tin** mở đúng hội thoại với giáo viên đó.
- Hộp thư: nhãn số tin chưa đọc trên từng hội thoại và trên tab "Tin nhắn". Ẩn các thao tác xóa/ghim/lưu trữ giả (hoặc giữ SnackBar nhưng ghi rõ "sắp có").

### GĐ C: Cập nhật gần thời gian thực và thông báo (BE ~0,5 ngày, FE ~0,5 ngày)
> **Đã làm (25/10):** BE `GET /api/messages/conversation/{id}?after=` (ISO-8601); `NotificationService.notifyNewMessage` (gộp: còn thông báo NEW_MESSAGE chưa đọc của hội thoại thì không tạo thêm), `notifyNewOffer`, `notifyOfferAccepted` (đều `@Async`, lỗi ghi thông báo không rollback báo giá); FE đọc qua API có sẵn `GET /api/v1/notifications/my-notifications`. Test: `ChatNotificationTest`. FE: chat hỏi tin mới 5 giây/lần, hộp thư 30 giây/lần, dừng khi app vào nền, tự cuộn, lọc trùng. **Còn lại:** màn danh sách thông báo phía FE (API đã có).
**BE**
- `GET /api/messages/conversation/{id}?after=<createdAt>`: chỉ trả tin mới, để hỏi lại nhẹ.
- `NotificationService` thêm 3 loại, ghi vào bảng `notifications` có sẵn:
  - `NEW_MESSAGE`, gửi cho bên còn lại; gộp nhiều tin liên tiếp thành một thông báo chưa đọc;
  - `NEW_OFFER`, gửi cho phụ huynh;
  - `OFFER_ACCEPTED`, gửi cho giáo viên.

**FE**
- Chat đang mở: cứ 5 giây hỏi `?after=` một lần, dừng khi rời màn hoặc app vào nền.
- Hộp thư: cứ 30 giây làm mới một lần, có thêm kéo để làm mới.
- Có tin mới khi đang ở cuối danh sách thì tự cuộn xuống.

### GĐ D: Hoàn thiện (~0,5 ngày)
> **Đã làm (25/10):** gửi tin lỗi mạng thì chữ vẫn trong ô nhập để gửi lại; test FE `test/chat_flow_test.dart` (chat có thẻ báo giá ở 400×900 và 360×640 cho cả hai vai trò, giáo viên gửi báo giá mới thì báo giá cũ hết hiệu lực, lỗi mạng giữ chữ, badge chưa đọc, tab Báo giá ở 360×640). E2E chạy BE thật với Postgres tạm (schema Supabase): 40/40 điểm kiểm tra từ đăng yêu cầu tới tạo đơn thanh toán. Bước PayOS → FUNDED kiểm bằng `CustomizePaymentFundedTest` (cần khóa PayOS thật để chạy qua HTTP).
- Trạng thái lỗi: mạng lỗi khi gửi thì tin hiện "Gửi lại", chứ không mất chữ đã gõ.
- Kiểm tra giao diện ở 400×900 và 360×640: thẻ báo giá, bàn phím, thanh điều hướng nổi.
- Test widget cho cả hai vai trò (`MockClient`):
  - giáo viên gửi báo giá thì báo giá cũ hiện "hết hiệu lực";
  - phụ huynh chấp nhận trong chat thì hiện nút Thanh toán;
  - có nhãn chưa đọc;
  - hỏi lại thì thêm được tin mới.
- Cập nhật `CLAUDE.md` của FE, và `docs/customize-flow-review.md`.

## Ngoài phạm vi
- Gửi ảnh hoặc tệp trong chat.
- Push notification (FCM).
- WebSocket.
- Chat theo bài học giữa bé và giáo viên. Dùng chung hạ tầng nhưng không làm giao diện ở đây.
- Thu hồi/sửa tin nhắn.
- Staff đọc hội thoại để giải quyết khiếu nại.

## Rủi ro
- Thêm cột phải chạy SQL trên Supabase **trước** khi deploy BE mới. Không thì entity có cột mà DB không có, và truy vấn tin nhắn lỗi.
- Hỏi lại server mỗi 5 giây làm tăng tải khi nhiều người mở chat cùng lúc. Chấp nhận được ở quy mô đồ án; nếu cần thì chuyển sang WebSocket.
- Tin báo giá cũ (tạo trước GĐ A) không có `offer_id`. FE vẫn phải đoán cho các tin này: tin báo giá cuối của giáo viên đó là báo giá hiện hành.

## Kiểm tra end-to-end (sau GĐ B)
Dùng hai tài khoản thật (một giáo viên, một phụ huynh):
1. Phụ huynh đăng yêu cầu.
2. Giáo viên mở Bảng yêu cầu, bấm Nhắn tin, gửi tin, gửi báo giá A, rồi báo giá B. A phải hiện "hết hiệu lực".
3. Phụ huynh thấy nhãn chưa đọc, mở chat, trả lời, rồi chấp nhận B. Yêu cầu chuyển "Chờ thanh toán", giáo viên thấy "Đã chấp nhận".
4. Thanh toán PayOS sandbox. Yêu cầu phải vào tab "Đang làm" của giáo viên.
