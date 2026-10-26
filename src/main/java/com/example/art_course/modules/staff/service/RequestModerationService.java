package com.example.art_course.modules.staff.service;

import com.example.art_course.common.AppError;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.contracts.IModerationContract;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.customize_request.service.CustomizeRequestService;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.staff.dto.RequestReviewItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * STF-01/07: Staff duyệt bài đăng yêu cầu customize. Duyệt thì bài OPEN (hiện cho giáo viên báo giá);
 * từ chối thì REJECTED kèm lý do, chủ bài sửa và gửi lại, bị từ chối đủ MAX_REJECTIONS lần thì bị từ chối hẳn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestModerationService {

    private final CustomizeRequestRepository requestRepository;
    private final IModerationContract moderationContract;
    private final NotificationService notificationService;
    private final TeacherNames names;

    @Transactional(readOnly = true)
    public List<RequestReviewItemDto> pending() {
        List<CustomizeRequest> requests = requestRepository.findAllByStatus(CustomizeRequestService.PENDING_REVIEW);
        Map<UUID, String> parents = names.parentsByIds(requests.stream().map(CustomizeRequest::getParentId).distinct().toList());
        return requests.stream()
                .map(r -> new RequestReviewItemDto(r.getId(), r.getParentId(), parents.get(r.getParentId()),
                        r.getTitle(), r.getDescription(), r.getCategory(), r.getChildAge(), r.getBudget(),
                        r.getDeadline(), r.getRejectionCount()))
                .toList();
    }

    @Transactional
    public void approve(UUID requestId, UUID staffId) {
        CustomizeRequest request = lockPending(requestId);
        request.setStatus(CustomizeRequestService.OPEN);
        request.setRejectionCount(0);
        request.setReviewNote(null);
        requestRepository.save(request);

        moderationContract.recordModerationReview(staffId, "CUSTOMIZE_REQUEST", requestId, "APPROVED", null);
        notificationService.notifyRequestApproved(requestId, request.getParentId(), request.getTitle());
        log.info("[REQUEST-APPROVE] Staff {} đã duyệt bài đăng {}", staffId, requestId);
    }

    @Transactional
    public void reject(UUID requestId, UUID staffId, String reason) {
        CustomizeRequest request = lockPending(requestId);
        request.setStatus(CustomizeRequestService.REJECTED);
        request.setRejectionCount(request.getRejectionCount() + 1);
        request.setReviewNote(reason);
        requestRepository.save(request);

        int remaining = Math.max(0, CustomizeRequestService.MAX_REJECTIONS - request.getRejectionCount());
        moderationContract.recordModerationReview(staffId, "CUSTOMIZE_REQUEST", requestId, "REJECTED", reason);
        notificationService.notifyRequestRejected(requestId, request.getParentId(), request.getTitle(), reason, remaining);
        log.info("[REQUEST-REJECT] Staff {} đã từ chối bài đăng {} (lần {})", staffId, requestId, request.getRejectionCount());
    }

    // Khóa dòng để hai Staff cùng duyệt một bài thì chạy lần lượt; chỉ bài đang chờ duyệt mới quyết định được.
    private CustomizeRequest lockPending(UUID requestId) {
        CustomizeRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Không tìm thấy bài đăng"));
        if (!CustomizeRequestService.PENDING_REVIEW.equalsIgnoreCase(request.getStatus())) {
            throw new AppError(HttpStatus.CONFLICT, "INVALID_STATUS", "Bài đăng không ở trạng thái chờ duyệt (PENDING_REVIEW)");
        }
        return request;
    }
}
