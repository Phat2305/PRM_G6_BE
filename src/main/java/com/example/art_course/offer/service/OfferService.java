
package com.example.art_course.offer.service;

import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.customize_request.service.CustomizeRequestService;
import com.example.art_course.messaging.entity.Conversation;
import com.example.art_course.messaging.entity.Message;
import com.example.art_course.messaging.repository.ConversationRepository;
import com.example.art_course.messaging.repository.MessageRepository;
import com.example.art_course.offer.dto.CreateOfferRequest;
import com.example.art_course.offer.dto.OfferResponse;
import com.example.art_course.offer.dto.UpdateOfferStatusRequest;
import com.example.art_course.offer.entity.Offer;
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

@Service
public class OfferService {

    // superseded: bị báo giá mới hơn của cùng giáo viên thay thế (CUS-05)
    private static final Set<String> ALLOWED_STATUSES =
            Set.of("pending", "accepted", "rejected", "cancelled", "superseded");

    private final OfferRepository offerRepository;
    private final CustomizeRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public OfferService(
            OfferRepository offerRepository,
            CustomizeRequestRepository requestRepository,
            UserRepository userRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository) {
        this.offerRepository = offerRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public OfferResponse create(
            CreateOfferRequest request, String username) {

        User user = getUser(username);
        requireRole(user, "TEACHER");

        if (request.getRequestId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customize request ID is required");
        }

        if (request.getPrice() == null
                || request.getPrice().signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Offer price must be zero or greater");
        }

        CustomizeRequest customizeRequest =
                findCustomizeRequest(request.getRequestId());

        if (!CustomizeRequestService.OPEN.equalsIgnoreCase(
                customizeRequest.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot create an offer for this request");
        }

        Integer days = request.getDeliveryDays();
        if (days != null && (days < 1 || days > 60)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Delivery days must be between 1 and 60");
        }

        // Gửi kèm hội thoại: hội thoại phải của chính giáo viên và đúng yêu cầu này.
        Conversation conversation = null;
        if (request.getConversationId() != null) {
            conversation = conversationRepository.findById(request.getConversationId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Conversation not found"));
            if (!user.getId().equals(conversation.getTeacherId())
                    || !customizeRequest.getId().equals(conversation.getContextId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Conversation does not belong to this request");
            }
        }

        // CUS-05: báo giá mới thay mọi báo giá đang chờ của cùng giáo viên cho yêu cầu này.
        for (Offer old : offerRepository.findAllByRequestIdAndTeacherId(
                customizeRequest.getId(), user.getId())) {
            if ("pending".equalsIgnoreCase(old.getStatus())) {
                old.setStatus("superseded");
                offerRepository.save(old);
            }
        }

        Offer offer = offerRepository.save(Offer.builder()
                .requestId(customizeRequest.getId())
                .teacherId(user.getId())
                .price(request.getPrice())
                .message(request.getMessage())
                .deliveryDays(days)
                .status("pending")
                .build());

        // Tin báo giá tạo cùng transaction: lỗi thì không có báo giá "mồ côi" thiếu tin, hay ngược lại.
        OfferResponse response = toResponse(offer);
        if (conversation != null) {
            String note = request.getMessage();
            Message message = messageRepository.save(Message.builder()
                    .conversationId(conversation.getId())
                    .senderId(user.getId())
                    .content(note == null || note.isBlank() ? "Báo giá" : note.trim())
                    .isOffer(true)
                    .offerId(offer.getId())
                    .build());
            response.setMessageId(message.getId());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public OfferResponse getById(UUID id, String username) {
        User user = getUser(username);
        Offer offer = findById(id);

        requireCanView(offer, user);

        return toResponse(offer);
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> getByRequestId(
            UUID requestId, String username) {

        User user = getUser(username);
        CustomizeRequest customizeRequest =
                findCustomizeRequest(requestId);

        if (!isRequestOwner(customizeRequest, user)
                && !hasRole(user, "ADMIN")
                && !hasRole(user, "TEACHER")) {
            throw forbidden();
        }

        List<Offer> offers =
                offerRepository.findAllByRequestId(requestId);

        if (hasRole(user, "TEACHER")) {
            offers = offers.stream()
                    .filter(offer ->
                            user.getId().equals(offer.getTeacherId()))
                    .toList();
        }

        return offers.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> getByTeacherId(
            UUID teacherId, String username) {

        User user = getUser(username);

        if (!hasRole(user, "ADMIN")
                && !user.getId().equals(teacherId)) {
            throw forbidden();
        }

        return offerRepository.findAllByTeacherId(teacherId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> getByStatus(
            String status, String username) {

        User user = getUser(username);
        requireRole(user, "ADMIN");

        String normalized = normalizeStatus(status);

        return offerRepository.findAllByStatus(normalized)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OfferResponse updateStatus(
            UUID id,
            UpdateOfferStatusRequest request,
            String username) {

        User user = getUser(username);
        Offer offer = findById(id);
        CustomizeRequest customizeRequest =
                findCustomizeRequest(offer.getRequestId());

        String status = normalizeStatus(request.getStatus());

        if (hasRole(user, "ADMIN")) {
            offer.setStatus(status);
        } else if (isRequestOwner(customizeRequest, user)) {
            // The parent who owns the request can respond to an offer.
            if (!Set.of("accepted", "rejected")
                    .contains(status)) {
                throw forbidden();
            }

            if (!"pending".equalsIgnoreCase(offer.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Only pending offers can be accepted or rejected");
            }

            if ("accepted".equals(status)) {
                acceptForRequest(offer, customizeRequest);
            }

            offer.setStatus(status);
        } else if (hasRole(user, "TEACHER")
                && user.getId().equals(offer.getTeacherId())) {

            // Teacher can cancel their own pending offer.
            if (!"cancelled".equals(status)
                    || !"pending".equalsIgnoreCase(offer.getStatus())) {
                throw forbidden();
            }

            offer.setStatus(status);
        } else {
            throw forbidden();
        }

        return toResponse(offerRepository.save(offer));
    }

    // CUS-05/08: chỉ chấp nhận một báo giá cho yêu cầu đang OPEN; yêu cầu sang ACCEPTED (chờ thanh toán),
    // các báo giá còn chờ của yêu cầu đó bị từ chối.
    private void acceptForRequest(Offer accepted, CustomizeRequest request) {
        if (!CustomizeRequestService.OPEN.equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This request already has an accepted offer");
        }
        request.setStatus("ACCEPTED");
        requestRepository.save(request);

        for (Offer other : offerRepository.findAllByRequestId(request.getId())) {
            if (!other.getId().equals(accepted.getId())
                    && "pending".equalsIgnoreCase(other.getStatus())) {
                other.setStatus("rejected");
                offerRepository.save(other);
            }
        }
    }

    @Transactional
    public void delete(UUID id, String username) {
        User user = getUser(username);
        Offer offer = findById(id);

        if (!hasRole(user, "ADMIN")
                && !(hasRole(user, "TEACHER")
                && user.getId().equals(offer.getTeacherId())
                && "pending".equalsIgnoreCase(offer.getStatus()))) {
            throw forbidden();
        }

        offerRepository.delete(offer);
    }

    private User getUser(String username) {
        return userRepository.findByPrincipal(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));
    }

    private Offer findById(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Offer ID is required");
        }

        return offerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Offer not found"));
    }

    private CustomizeRequest findCustomizeRequest(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customize request not found"));
    }

    private boolean isRequestOwner(
            CustomizeRequest request, User user) {
        return request.getParentId() != null
                && request.getParentId().equals(user.getId());
    }

    private void requireCanView(Offer offer, User user) {
        CustomizeRequest request =
                findCustomizeRequest(offer.getRequestId());

        boolean allowed =
                hasRole(user, "ADMIN")
                        || isRequestOwner(request, user)
                        || (hasRole(user, "TEACHER")
                        && user.getId().equals(offer.getTeacherId()));

        if (!allowed) {
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
                && user.getRole().trim()
                .equalsIgnoreCase(role);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status is required");
        }

        String normalized = status.trim().toLowerCase(Locale.ROOT);

        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid offer status");
        }

        return normalized;
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not allowed to perform this action");
    }

    private OfferResponse toResponse(Offer offer) {
        return OfferResponse.builder()
                .id(offer.getId())
                .requestId(offer.getRequestId())
                .teacherId(offer.getTeacherId())
                .price(offer.getPrice())
                .message(offer.getMessage())
                .status(offer.getStatus())
                .deliveryDays(offer.getDeliveryDays())
                .build();
    }
}
