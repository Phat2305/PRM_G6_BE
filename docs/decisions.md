Quyết định đã chốt (đặt tại docs/decisions.md)

Các quyết định này ghi đè mọi chỗ cũ trong ảnh use case và checklist.

SHOP-06 (29/10/2026, yêu cầu người dùng): mỗi phụ huynh dùng một địa chỉ trong `parent_profiles.shipping_address`, tên/SĐT dùng chung `full_name`/`phone`. FE đọc/lưu qua GET/PUT `/api/v1/users/me`; bỏ API `/me/addresses`. Khi chuyển đổi, giữ shipping_address đã có; nếu trống thì chép địa chỉ mặc định từ parent_addresses, chỉ bù phone còn trống và giữ tên hồ sơ. Xuất bản sao dữ liệu cũ, cập nhật BE/FE rồi chạy thủ công [V10__parent_profile_address.sql](../src/main/resources/db/migration/V10__parent_profile_address.sql) để chuyển dữ liệu và xóa bảng cũ; không dùng CASCADE. Địa chỉ phụ và người nhận riêng không được chuyển sang hồ sơ. Không chạy lại khối B5 của migration 08 sau bước này.

#	Quyết định
1	Khóa học không có trường trình độ (SRS CRS-01). Chưa thêm cột độ tuổi cho khóa vì #6 SRS chưa chốt. "Art Kit" là học cụ gợi ý tổng hợp từ các bài, bán giá lẻ, không có giá gói (CRS-08, #15).
2	Chỉ Teacher upload video. UC 6.1 bỏ "Admin".
3	READY tính theo từng video/bài. Khóa chỉ công khai khi Staff duyệt và mọi video READY.
4	UC 6.2 đổi tên thành "Course Builder"; bỏ ghi chú "Customize chưa định nghĩa" (đã định nghĩa ở CUS-10).
5	Bỏ ghi chú "nhiều Teacher trong một đơn" (đã có BR-04).
6	Giải phóng tiền cả khoản thanh toán khi mọi dòng trong đơn đủ điều kiện (#26). Task D "từng loại sản phẩm" sửa theo quyết định này.
7	Escrow chỉ dựng khung: mốc N, M, X do Admin cấu hình; hoàn tiền và tranh chấp Staff xử lý từng ca (#2, #3, #4).
8	Phạm vi là Backend 2, chung một codebase với Backend 1. BE2 sở hữu: giỏ hàng, đơn hàng, thanh toán, giao hàng, dòng tiền, Staff/Admin, thông báo, job nền. Bảng của BE1 chỉ đọc; cần đổi thì ghi vào handoff-be1.md.
9	Quy trình Staff duyệt khóa học (cập nhật 26/10): DRAFT → PENDING_REVIEW → PUBLISHED/REJECTED. Giáo viên chỉ đặt DRAFT/PENDING_REVIEW. Gửi duyệt cần mọi video READY (khóa thường cần thêm giá > 0). Bị từ chối tối đa 3 lần (`rejection_count`, về 0 khi được duyệt); lần thứ 3 thì không gửi lại được, kể cả đổi về DRAFT. Sửa khóa đã công khai (thông tin, bài học, video) thì khóa về PENDING_REVIEW và gỡ khỏi danh mục tới khi được duyệt lại; người đã mua giữ quyền học.
10	Khóa customize: Staff duyệt trước, phụ huynh sau. Giáo viên gửi khóa (`/deliver`) → yêu cầu vẫn FUNDED, khóa PENDING_REVIEW; Staff duyệt → yêu cầu DELIVERED và báo phụ huynh. Phụ huynh chưa đồng ý (`/reject-delivery`, bắt buộc lý do): lý do gửi cho giáo viên qua tin nhắn của yêu cầu, yêu cầu về FUNDED, khóa về DRAFT để sửa và gửi Staff duyệt lại. Không thống nhất được thì phụ huynh xin hoàn tiền (`/refunds/request` cho dòng đơn của yêu cầu); Staff duyệt hoàn tiền thì yêu cầu CANCELLED. Khóa customize bị Staff từ chối đủ 3 lần thì yêu cầu CANCELLED để phụ huynh xin hoàn tiền.
11	Bài đăng (yêu cầu customize): tạo ra ở PENDING_REVIEW, Staff duyệt → OPEN (hiện cho giáo viên báo giá), từ chối → REJECTED kèm lý do. Chủ bài sửa bài đang OPEN thì bài đóng lại (PENDING_REVIEW) tới khi Staff duyệt lại; bài bị từ chối sửa rồi gửi lại được tới khi đủ 3 lần từ chối thì từ chối hẳn. Giáo viên chỉ thấy bài OPEN (hoặc bài mình đã báo giá). Đổi trạng thái thủ công chỉ Admin; chủ bài đóng bài bằng `/cancel`.
12	Video HLS (LRN-03): chỉ Staff/Admin, giáo viên sở hữu khóa, bé đã được cấp quyền học, phụ huynh có bé đã được cấp quyền (hoặc chủ yêu cầu của khóa riêng đã được Staff duyệt) và bài xem thử (is_trial) mới xem được; giáo viên không tự đặt `videoUrl`/trạng thái video qua API bài học (chỉ đường tải lên + xử lý đặt).
13	Gỡ khóa công khai vi phạm (STF-06): Staff gọi POST /api/v1/staff/courses/{id}/hide kèm lý do; chỉ đổi status sang HIDDEN (không xóa), ghi moderation_reviews với quyết định FLAGGED và báo giáo viên (COURSE_HIDDEN). Khóa HIDDEN khỏi danh mục, giỏ và đặt hàng; người đã mua vẫn học được. Giáo viên sửa rồi gửi duyệt lại như khóa bị từ chối, bộ đếm từ chối không đổi. Khóa customize không gỡ được. Ảnh bìa là điều kiện gửi duyệt và duyệt khóa thường; khóa customize được miễn.

14	PAY-08: tiền PayOS đến sau khi đơn CANCELLED/EXPIRED không khôi phục đơn/kho/quyền học; tạo một yêu cầu hoàn tiền toàn bộ để Staff xử lý thủ công. Link thanh toán chưa xác nhận hủy được dùng lại khi thử thanh toán.
15	FIN-09: REFUND ghi phần giáo viên chịu (gross hoàn trừ commission snapshot). Hoàn từ HELD không trừ số dư khả dụng của đơn khác; affects_available=false và giảm phần ký quỹ còn lại. Hoàn từ RELEASED trừ khả dụng. Hoàn đủ gross đổi payment sang REFUNDED. Không tự sửa dữ liệu sổ cái lịch sử.
16	SHOP-03: thông tin nhận hàng của đơn là snapshot riêng trong orders; đổi parent_profiles.shipping_address không đổi địa chỉ đơn cũ. Checkout chỉ gán khóa cho bé thuộc phụ huynh.

17	FIN-09 (yêu cầu giao diện Staff): màn Hoàn tiền thay nhập mã giao dịch bằng ảnh JPG/PNG/WEBP tối đa 5 MB. Upload multipart và hoàn tất dùng cùng khóa/transaction; proof_image_url trỏ endpoint riêng tư, chỉ Staff/Admin và phụ huynh sở hữu được xem. Mã giao dịch để trống trên luồng ảnh; giữ mã lịch sử và API JSON cũ cho client cũ. Không đổi schema.

18	CUS-12, SHOP-09: khóa customize chỉ được đánh giá sau khi phụ huynh nhận khóa và yêu cầu COMPLETED. Người đánh giá phải là chủ yêu cầu, có bé đang được cấp quyền học khóa đó. FE dùng ID khóa từ bản bàn giao đã nhận; đơn thật thiếu ID khóa hoặc API lưu thất bại không được báo đánh giá thành công. Khóa thường giữ điều kiện đánh giá theo quyền học hiện có.

19	FIN-04 (yêu cầu người dùng): giáo viên rút tối thiểu 10.000 VND mỗi yêu cầu, áp dụng cả FE và BE; vẫn không được vượt số dư khả dụng và chỉ có tối đa một yêu cầu PENDING/APPROVED.

20	FIN-06 (yêu cầu người dùng): Staff xác nhận đã chi rút tiền bằng ảnh JPG/PNG/WEBP tối đa 5 MB thay nhập mã giao dịch. Upload và ghi PAID dùng chung khóa yêu cầu/transaction; lưu proof_image_url có sẵn, mã giao dịch để trống trên luồng ảnh. Chỉ giáo viên sở hữu và Staff/Admin được xem bằng chứng. Giữ API JSON/mã giao dịch lịch sử cho client cũ; không đổi schema.

21	ADM-04/05: nhật ký Staff và sổ cái kèm thông tin đối tượng/đơn hiện tại, mặt hàng và ảnh bằng chứng riêng tư khi có. Nhật ký bổ sung chi/hoàn tiền và từ chối rút tiền từ bút toán đã ghi bởi Staff, không dựng lại sự kiện chưa từng được ghi. Giữ nguyên lịch sử/số tiền/ghi chú gốc; FE hiển thị mã DH/HT/RT và 8 ký tự đầu UUID, có sao chép UUID gốc để đối soát. Mã ngắn chỉ để hiển thị, không thay khóa định danh trong DB/API.
