Quyết định đã chốt (đặt tại docs/decisions.md)

Các quyết định này ghi đè mọi chỗ cũ trong ảnh use case và checklist.

#	Quyết định
1	Khóa học không có trường trình độ (SRS CRS-01). Chưa thêm cột độ tuổi cho khóa vì #6 SRS chưa chốt. "Art Kit" là học cụ gợi ý tổng hợp từ các bài, bán giá lẻ, không có giá gói (CRS-08, #15).
2	Chỉ Teacher upload video. UC 6.1 bỏ "Admin".
3	READY tính theo từng video/bài. Khóa chỉ công khai khi Staff duyệt và mọi video READY.
4	UC 6.2 đổi tên thành "Course Builder"; bỏ ghi chú "Customize chưa định nghĩa" (đã định nghĩa ở CUS-10).
5	Bỏ ghi chú "nhiều Teacher trong một đơn" (đã có BR-04).
6	Giải phóng tiền cả khoản thanh toán khi mọi dòng trong đơn đủ điều kiện (#26). Task D "từng loại sản phẩm" sửa theo quyết định này.
7	Escrow chỉ dựng khung: mốc N, M, X do Admin cấu hình; hoàn tiền và tranh chấp Staff xử lý từng ca (#2, #3, #4).
8	Phạm vi là Backend 2, chung một codebase với Backend 1. BE2 sở hữu: giỏ hàng, đơn hàng, thanh toán, giao hàng, dòng tiền, Staff/Admin, thông báo, job nền. Bảng của BE1 chỉ đọc; cần đổi thì ghi vào handoff-be1.md.