
package com.example.art_course.customize_request.service;

import com.example.art_course.customize_request.dto.CreateCustomizeRequestRequest;
import com.example.art_course.customize_request.dto.CustomizeRequestResponse;
import com.example.art_course.customize_request.dto.UpdateCustomizeRequestRequest;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Bài đăng yêu cầu customize của phụ huynh (CUS-01..). Bài mới ở PENDING_REVIEW cho tới khi Staff duyệt thì OPEN
 * (hiện cho giáo viên báo giá). Staff từ chối thì REJECTED, chủ bài sửa rồi gửi lại, bị từ chối đủ MAX_REJECTIONS lần
 * thì bị từ chối hẳn. Sửa bài đang OPEN thì bài đóng lại (PENDING_REVIEW) tới khi Staff duyệt lại.
 */
@Service
public class CustomizeRequestService {

    // Tên trạng thái theo SRS; dữ liệu trong DB cũng lưu dạng này.
    public static final String OPEN = "OPEN";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String REJECTED = "REJECTED";
    public static final int MAX_REJECTIONS = 3;
    private static final Set<String> ALLOWED_STATUSES = Set.of(OPEN, PENDING_REVIEW, "ACCEPTED", "FUNDED",
            "DELIVERED", "COMPLETED", REJECTED, "CANCELLED");

    private final CustomizeRequestRepository repository;
    private final UserRepository userRepository;
    private final OfferRepository offerRepository;

    public CustomizeRequestService(
            CustomizeRequestRepository repository,
            UserRepository userRepository,
            OfferRepository offerRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
    }

    @Transactional
    public CustomizeRequestResponse create(
            CreateCustomizeRequestRequest request,
            String username) {

        if (request.getTitle() == null
                || request.getTitle().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request title is required");
        }

        User user = getUser(username);
        requireRole(user, "PARENT");

        CustomizeRequest entity = CustomizeRequest.builder()
                .parentId(user.getId())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .category(request.getCategory())
                .childAge(request.getChildAge())
                .budget(request.getBudget())
                .deadline(request.getDeadline())
                .status(PENDING_REVIEW)
                .build();

        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public CustomizeRequestResponse getById(
            UUID id, String username) {

        User user = getUser(username);
        CustomizeRequest entity = findById(id);

        if (!isOwner(entity, user)
                && !hasRole(user, "ADMIN")
                && !teacherMayRead(entity, user)) {
            throw forbidden();
        }

        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CustomizeRequestResponse> getByParentId(
            UUID parentId, String username) {

        User user = getUser(username);
        boolean seesAll = user.getId().equals(parentId) || hasRole(user, "ADMIN");

        if (!seesAll && !hasRole(user, "TEACHER")) {
            throw forbidden();
        }

        // Giáo viên chỉ thấy bài đã được duyệt (OPEN) của phụ huynh này.
        return toResponses(repository.findAllByParentId(parentId)
                .stream()
                .filter(r -> seesAll || OPEN.equalsIgnoreCase(r.getStatus()))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<CustomizeRequestResponse> getByStatus(
            String status, String username) {

        User user = getUser(username);
        requireRequestManager(user);

        String normalized = normalizeStatus(status);
        // Giáo viên chỉ xem được bảng bài đã duyệt; mọi trạng thái khác (kể cả bài chờ duyệt) là của Admin.
        if (!hasRole(user, "ADMIN") && !OPEN.equals(normalized)) {
            throw forbidden();
        }

        return toResponses(repository.findAllByStatus(normalized));
    }

    @Transactional(readOnly = true)
    public List<CustomizeRequestResponse> getByCategory(
            String category, String username) {

        User user = getUser(username);
        requireRequestManager(user);

        if (category == null || category.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category is required");
        }

        boolean admin = hasRole(user, "ADMIN");
        return toResponses(repository.findAllByCategoryIgnoreCase(category.trim())
                .stream()
                .filter(r -> admin || OPEN.equalsIgnoreCase(r.getStatus()))
                .toList());
    }

    @Transactional
    public CustomizeRequestResponse update(
            UUID id,
            UpdateCustomizeRequestRequest request,
            String username) {

        User user = getUser(username);
        CustomizeRequest entity = findById(id);

        requireOwner(entity, user);
        requireEditable(entity);

        if (request.getTitle() == null
                || request.getTitle().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request title is required");
        }

        entity.setTitle(request.getTitle().trim());
        entity.setDescription(request.getDescription());
        entity.setCategory(request.getCategory());
        entity.setChildAge(request.getChildAge());
        entity.setBudget(request.getBudget());
        entity.setDeadline(request.getDeadline());
        // Sửa là gửi duyệt lại: bài đang OPEN đóng lại, bài bị từ chối được gửi lại; Staff duyệt thì mới (mở lại) OPEN.
        entity.setStatus(PENDING_REVIEW);

        return toResponse(repository.save(entity));
    }

    // Đặt trạng thái thủ công: chỉ Admin. Các bước thường ngày đi qua API riêng (duyệt, chấp nhận báo giá,
    // thanh toán, bàn giao, xác nhận) nên không còn để giáo viên đổi trạng thái bài của người khác.
    @Transactional
    public CustomizeRequestResponse updateStatus(
            UUID id, String status, String username) {

        User user = getUser(username);
        requireRole(user, "ADMIN");

        String normalized = normalizeStatus(status);
        CustomizeRequest entity = findById(id);

        entity.setStatus(normalized);

        return toResponse(repository.save(entity));
    }

    // Chủ bài đóng yêu cầu chưa có người nhận làm (OPEN/PENDING_REVIEW/REJECTED): CANCELLED, giữ lại lịch sử.
    @Transactional
    public CustomizeRequestResponse cancel(UUID id, String username) {
        User user = getUser(username);
        CustomizeRequest entity = findById(id);

        requireOwner(entity, user);
        if (!isEditableStatus(entity.getStatus()) && !REJECTED.equalsIgnoreCase(entity.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only open, pending-review or rejected requests can be closed");
        }
        entity.setStatus("CANCELLED");

        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id, String username) {
        User user = getUser(username);
        CustomizeRequest entity = findById(id);

        requireOwner(entity, user);
        // Chỉ xóa được bài chưa có người nhận làm: đang chờ duyệt, đã mở hoặc bị từ chối.
        if (!isEditableStatus(entity.getStatus()) && !REJECTED.equalsIgnoreCase(entity.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only open, pending-review or rejected requests can be deleted");
        }

        repository.delete(entity);
    }

    private User getUser(String username) {
        return userRepository.findByPrincipal(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));
    }

    private CustomizeRequest findById(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request ID is required");
        }

        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customize request not found"));
    }

    private boolean isOwner(
            CustomizeRequest entity, User user) {
        return entity.getParentId() != null
                && entity.getParentId().equals(user.getId());
    }

    private void requireOwner(
            CustomizeRequest entity, User user) {
        if (!isOwner(entity, user)) {
            throw forbidden();
        }
    }

    // Giáo viên đọc được bài đã duyệt (OPEN) hoặc bài mình đã báo giá (để theo dõi việc đang làm).
    private boolean teacherMayRead(CustomizeRequest entity, User user) {
        if (!hasRole(user, "TEACHER")) {
            return false;
        }
        return OPEN.equalsIgnoreCase(entity.getStatus())
                || !offerRepository.findAllByRequestIdAndTeacherId(entity.getId(), user.getId()).isEmpty();
    }

    private boolean isEditableStatus(String status) {
        return OPEN.equalsIgnoreCase(status) || PENDING_REVIEW.equalsIgnoreCase(status);
    }

    // Sửa được khi OPEN, đang chờ duyệt, hoặc bị từ chối mà chưa đủ MAX_REJECTIONS lần.
    private void requireEditable(CustomizeRequest entity) {
        if (REJECTED.equalsIgnoreCase(entity.getStatus())) {
            if (entity.getRejectionCount() >= MAX_REJECTIONS) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Request was rejected " + MAX_REJECTIONS + " times and cannot be resubmitted");
            }
            return;
        }
        if (!isEditableStatus(entity.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only open, pending-review or rejected requests can be edited");
        }
    }

    private boolean canManageRequests(User user) {
        return hasRole(user, "TEACHER")
                || hasRole(user, "ADMIN");
    }

    private void requireRequestManager(User user) {
        if (!canManageRequests(user)) {
            throw forbidden();
        }
    }

    private void requireRole(User user, String role) {
        if (!hasRole(user, role)) {
            throw forbidden();
        }
    }

    private boolean hasRole(User user, String role) {
        return user.getRole() != null
                && user.getRole().trim().equalsIgnoreCase(role);
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status is required");
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        // App cũ vẫn gửi "pending"
        if ("PENDING".equals(normalized)) {
            normalized = OPEN;
        }

        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid request status");
        }

        return normalized;
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not allowed to access this request");
    }

    private CustomizeRequestResponse toResponse(
            CustomizeRequest entity) {
        return toResponses(List.of(entity)).getFirst();
    }

    private List<CustomizeRequestResponse> toResponses(List<CustomizeRequest> entities) {
        List<UUID> ids = entities.stream().map(CustomizeRequest::getId).filter(Objects::nonNull).toList();
        Map<UUID, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (var count : repository.countTeacherContacts(ids)) {
                counts.put(count.getRequestId(), count.getContacted());
            }
        }
        return entities.stream().map(entity -> CustomizeRequestResponse.builder()
                .id(entity.getId())
                .parentId(entity.getParentId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .childAge(entity.getChildAge())
                .budget(entity.getBudget())
                .deadline(entity.getDeadline())
                .status(entity.getStatus())
                .contacted(counts.getOrDefault(entity.getId(), 0L))
                .rejectionCount(entity.getRejectionCount())
                .reviewNote(entity.getReviewNote())
                .build()).toList();
    }
}
