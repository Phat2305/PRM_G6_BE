package com.example.art_course.contracts;

import java.util.UUID;

/**
 * Hợp đồng giao tiếp giữa BE2 và BE1 cho quyền học của trẻ em (IEnrollmentContract).
 */
public interface IEnrollmentContract {
    /**
     * Cấp quyền học cho Child ngay khi đơn hàng thanh toán thành công (PAY-03, BR-01).
     */
    void grantAccess(UUID childId, UUID courseId, UUID orderId);

    /**
     * Thu hồi quyền học khi khoản thanh toán của khóa học bị hoàn tiền (FIN-09, BR-12).
     */
    void revokeAccess(UUID childId, UUID courseId, UUID refundId);

    /**
     * Kiểm tra Child đã sở hữu khóa học hay chưa để chống mua trùng (SHOP-04).
     */
    boolean isEnrolled(UUID childId, UUID courseId);
}
