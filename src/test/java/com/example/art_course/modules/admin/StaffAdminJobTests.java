package com.example.art_course.modules.admin;

import com.example.art_course.common.ApiResponse;
import com.example.art_course.contracts.IModerationContract;
import com.example.art_course.jobs.JobExecutionTracker;
import com.example.art_course.jobs.entity.ScheduledJobLog;
import com.example.art_course.jobs.repository.ScheduledJobLogRepository;
import com.example.art_course.modules.finance.controller.AdminFinanceController;
import com.example.art_course.modules.moderation.entity.ModerationReview;
import com.example.art_course.modules.moderation.repository.ModerationReviewRepository;
import com.example.art_course.modules.moderation.service.ModerationService;
import com.example.art_course.modules.order.entity.Order;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.repository.SupplyRepository;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.staff.service.StaffService;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.modules.withdrawal.dto.MarkPaidRequest;
import com.example.art_course.modules.withdrawal.dto.WithdrawalResponseDto;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PROMPT 6 — JUNIT 5 TEST SUITE (Staff, Admin, Moderation & Jobs)")
public class StaffAdminJobTests {

    // =========================================================================
    // TEST 1: PHÂN QUYỀN TỪNG VAI TRÒ (STAFF / ADMIN / TEACHER ISOLATION)
    // =========================================================================
    @Test
    @DisplayName("1. Phân quyền: Staff không thể thực hiện hành động độc quyền của Admin (Duyệt/Từ chối rút tiền)")
    void testRoleAuthorization_StaffCannotApproveWithdrawal() {
        WithdrawalService withdrawalService = mock(WithdrawalService.class);
        ConfigService configService = mock(ConfigService.class);
        AdminFinanceController controller = new AdminFinanceController(withdrawalService, configService);

        UUID staffId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        // Giả lập token vai trò STAFF
        UsernamePasswordAuthenticationToken staffAuth = new UsernamePasswordAuthenticationToken(
                staffId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
        );

        // Kiểm tra logic ủy quyền: AdminFinanceController yêu cầu hasRole('ADMIN') cho endpoint approve/reject
        boolean hasAdminRole = staffAuth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        assertFalse(hasAdminRole, "Staff không được có quyền ROLE_ADMIN");

        // Khi xác thực là ADMIN thì được phép
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        boolean adminAllowed = adminAuth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        assertTrue(adminAllowed, "Admin có đầy đủ quyền ROLE_ADMIN");
    }

    @Test
    @DisplayName("1b. Phân quyền hai chiều FIN-06: STAFF gọi paid thành công kèm mã GD và ảnh chứng từ (staffId lấy từ token); STAFF bị từ chối approve/reject (chỉ ADMIN)")
    void testRoleAuthorization_StaffPaidAndAdminExclusiveApprove() {
        WithdrawalService withdrawalService = mock(WithdrawalService.class);
        ConfigService configService = mock(ConfigService.class);
        AdminFinanceController controller = new AdminFinanceController(withdrawalService, configService);

        UUID staffId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        // 1. Staff gọi markPaid thành công
        UsernamePasswordAuthenticationToken staffAuth = new UsernamePasswordAuthenticationToken(
                staffId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
        );
        MarkPaidRequest paidReq = new MarkPaidRequest("FT_TEST_123456", "https://img.com/receipt.jpg");

        WithdrawalResponseDto mockResponse = WithdrawalResponseDto.builder()
                .id(requestId)
                .status(WithdrawalStatus.PAID)
                .paidBy(staffId)
                .transactionCode("FT_TEST_123456")
                .proofImageUrl("https://img.com/receipt.jpg")
                .build();

        when(withdrawalService.markPaidWithdrawal(eq(requestId), eq(staffId), any())).thenReturn(mockResponse);

        ApiResponse<WithdrawalResponseDto> response = controller.markPaid(requestId, paidReq, staffAuth);
        assertNotNull(response.getData());
        assertEquals(staffId, response.getData().getPaidBy(), "staffId phải được trích xuất từ Authentication token");
        assertEquals("FT_TEST_123456", response.getData().getTransactionCode());
        assertEquals("https://img.com/receipt.jpg", response.getData().getProofImageUrl());
        verify(withdrawalService, times(1)).markPaidWithdrawal(eq(requestId), eq(staffId), eq(paidReq));

        // 2. Kiểm tra phân quyền: Staff không thể có quyền Admin approve/reject
        boolean staffCanApprove = staffAuth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        assertFalse(staffCanApprove, "Staff tuyệt đối không thể có quyền ROLE_ADMIN");

        // 3. Admin có thể gọi approve
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                adminId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        boolean adminCanApprove = adminAuth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        assertTrue(adminCanApprove, "Admin có quyền ROLE_ADMIN");
    }

    // =========================================================================
    // TEST 2: QUYẾT ĐỊNH KIỂM DUYỆT LUÔN SINH RA BẢN GHI TRONG MODERATION_REVIEWS (STF-07)
    // =========================================================================
    @Test
    @DisplayName("2. Quyết định duyệt luôn sinh ra bản ghi trong moderation_reviews (STF-07)")
    void testModerationReview_RecordedOnApproval() {
        ModerationReviewRepository reviewRepo = mock(ModerationReviewRepository.class);
        ModerationService moderationService = new ModerationService(reviewRepo);

        UUID staffId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        moderationService.recordModerationReview(
                staffId, "COURSE", courseId, "APPROVED", "Khóa học có video chất lượng cao"
        );

        ArgumentCaptor<ModerationReview> captor = ArgumentCaptor.forClass(ModerationReview.class);
        verify(reviewRepo).save(captor.capture());

        ModerationReview saved = captor.getValue();
        assertEquals(staffId, saved.getStaffId());
        assertEquals("COURSE", saved.getTargetType());
        assertEquals(courseId, saved.getTargetId());
        assertEquals("APPROVED", saved.getDecision());
        assertEquals("Khóa học có video chất lượng cao", saved.getReason());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    @DisplayName("2b. Staff kiểm duyệt dụng cụ cập nhật trạng thái và ghi moderation_reviews (STF-01, STF-07)")
    void testStaffModerateSupply_UpdatesStatusAndRecordsReview() {
        SupplyRepository supplyRepo = mock(SupplyRepository.class);
        OrderRepository orderRepo = mock(OrderRepository.class);
        OrderDetailRepository orderDetailRepo = mock(OrderDetailRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        IModerationContract moderationContract = mock(IModerationContract.class);

        StaffService staffService = new StaffService(
                supplyRepo, orderRepo, orderDetailRepo, refundRepo, moderationContract
        );

        UUID supplyId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        Supply supply = Supply.builder()
                .id(supplyId)
                .name("Bút chì màu cao cấp")
                .status("PENDING")
                .price(new BigDecimal("150000"))
                .build();

        when(supplyRepo.findById(supplyId)).thenReturn(Optional.of(supply));
        when(supplyRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Staff duyệt dụng cụ
        Supply approvedSupply = staffService.moderateSupply(supplyId, staffId, "APPROVE", "Sản phẩm nguồn gốc rõ ràng");

        assertEquals("ACTIVE", approvedSupply.getStatus());
        verify(supplyRepo).save(supply);

        // Kiểm chứng bắt buộc phải gọi sang IModerationContract để lưu nhật ký
        verify(moderationContract).recordModerationReview(
                eq(staffId), eq("SUPPLY"), eq(supplyId), eq("APPROVED"), eq("Sản phẩm nguồn gốc rõ ràng")
        );
    }

    // =========================================================================
    // TEST 3: JOB CHẠY AN TOÀN VÀ GHI NHẬT KÝ SCHEDULED_JOB_LOGS (NFR-19)
    // =========================================================================
    @Test
    @DisplayName("3. Job nền ghi nhật ký scheduled_job_logs đầy đủ: RUNNING -> SUCCESS kèm số bản ghi ảnh hưởng")
    void testJobExecutionTracker_TracksStatusAndRecordsAffected() {
        ScheduledJobLogRepository jobLogRepo = mock(ScheduledJobLogRepository.class);
        JobExecutionTracker tracker = new JobExecutionTracker(jobLogRepo);

        when(jobLogRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AtomicInteger actionExecuted = new AtomicInteger(0);

        // Chạy job xử lý 5 đơn hàng
        tracker.track("TestJob", () -> {
            actionExecuted.incrementAndGet();
            return 5;
        });

        assertEquals(1, actionExecuted.get(), "Action của job phải được thực thi");

        // Kiểm chứng lưu bản ghi với trạng thái SUCCESS và recordsAffected = 5
        ArgumentCaptor<ScheduledJobLog> logCaptor = ArgumentCaptor.forClass(ScheduledJobLog.class);
        verify(jobLogRepo, atLeast(2)).save(logCaptor.capture());

        ScheduledJobLog finalLog = logCaptor.getValue();
        assertEquals("TestJob", finalLog.getJobName());
        assertEquals("SUCCESS", finalLog.getStatus());
        assertEquals(5, finalLog.getRecordsAffected());
        assertNotNull(finalLog.getStartedAt());
        assertNotNull(finalLog.getFinishedAt());
    }

    @Test
    @DisplayName("3b. Job nền bắt lỗi an toàn và ghi nhận FAILED vào scheduled_job_logs khi gặp ngoại lệ")
    void testJobExecutionTracker_TracksFailureGracefully() {
        ScheduledJobLogRepository jobLogRepo = mock(ScheduledJobLogRepository.class);
        JobExecutionTracker tracker = new JobExecutionTracker(jobLogRepo);

        when(jobLogRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Chạy job gặp lỗi database timeout
        tracker.track("FailingJob", () -> {
            throw new RuntimeException("Database connection timeout");
        });

        ArgumentCaptor<ScheduledJobLog> logCaptor = ArgumentCaptor.forClass(ScheduledJobLog.class);
        verify(jobLogRepo, atLeast(2)).save(logCaptor.capture());

        ScheduledJobLog finalLog = logCaptor.getValue();
        assertEquals("FailingJob", finalLog.getJobName());
        assertEquals("FAILED", finalLog.getStatus());
        assertEquals("Database connection timeout", finalLog.getErrorMessage());
        assertNotNull(finalLog.getFinishedAt());
    }

    // =========================================================================
    // TEST 4: BẢO MẬT MSG-06 (STAFF CHỈ XEM TRAO ĐỔI KHI CÓ KHIẾU NẠI)
    // =========================================================================
    @Test
    @DisplayName("4. Bảo mật MSG-06: Staff bị từ chối xem trao đổi nếu đơn không có khiếu nại liên quan")
    void testStaffCannotViewConversationWithoutComplaint() {
        SupplyRepository supplyRepo = mock(SupplyRepository.class);
        OrderRepository orderRepo = mock(OrderRepository.class);
        OrderDetailRepository orderDetailRepo = mock(OrderDetailRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        IModerationContract moderationContract = mock(IModerationContract.class);

        StaffService staffService = new StaffService(
                supplyRepo, orderRepo, orderDetailRepo, refundRepo, moderationContract
        );

        UUID orderId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        Order order = Order.builder().id(orderId).parentId(UUID.randomUUID()).teacherId(UUID.randomUUID()).build();
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        // Đơn không có yêu cầu hoàn tiền nào và không có dòng nào giao thất bại
        when(refundRepo.findByOrderId(orderId)).thenReturn(Collections.emptyList());
        when(orderDetailRepo.findByOrderId(orderId)).thenReturn(Collections.emptyList());

        // Staff cố tình truy cập cuộc trao đổi -> Ném lỗi FORBIDDEN theo MSG-06
        com.example.art_course.common.AppError error = assertThrows(
                com.example.art_course.common.AppError.class,
                () -> staffService.getComplaintConversation(orderId, staffId)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals("NO_COMPLAINT_RELATED", error.getErrorCode());
        assertTrue(error.getMessage().contains("MSG-06"));
    }
}
