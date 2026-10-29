package com.example.art_course.customize_request;

import com.example.art_course.customize_request.dto.CreateCustomizeRequestRequest;
import com.example.art_course.customize_request.dto.UpdateCustomizeRequestRequest;
import com.example.art_course.customize_request.entity.CustomizeRequest;
import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import com.example.art_course.customize_request.service.CustomizeRequestService;
import com.example.art_course.offer.entity.Offer;
import com.example.art_course.offer.repository.OfferRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Bài đăng: PENDING_REVIEW -> (Staff duyệt) OPEN; sửa thì đóng lại chờ duyệt; bị từ chối đủ 3 lần thì từ chối hẳn.
// Trạng thái theo SRS (OPEN/ACCEPTED...); "pending" của app cũ vẫn được hiểu là OPEN.
class CustomizeRequestStatusTest {

    private final CustomizeRequestRepository repo = mock(CustomizeRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final CustomizeRequestService service = new CustomizeRequestService(repo, users, offers);

    @Test
    void listAndDetailIncludeContactCountsWithOneQueryPerResponse() {
        String parent = login("PARENT");
        UUID parentId = UUID.fromString(parent);
        CustomizeRequest working = saved(parentId, "FUNDED");
        CustomizeRequest open = saved(parentId, "OPEN");
        when(repo.findAllByParentId(parentId)).thenReturn(List.of(working, open));
        var count = mock(CustomizeRequestRepository.ContactCount.class);
        when(count.getRequestId()).thenReturn(working.getId());
        when(count.getContacted()).thenReturn(2L);
        when(repo.countTeacherContacts(List.of(working.getId(), open.getId()))).thenReturn(List.of(count));
        var list = service.getByParentId(parentId, parent);
        assertEquals(2L, list.getFirst().getContacted());
        assertEquals(0L, list.getLast().getContacted());
        verify(repo).countTeacherContacts(List.of(working.getId(), open.getId()));
        when(repo.countTeacherContacts(List.of(working.getId()))).thenReturn(List.of(count));
        assertEquals(2L, service.getById(working.getId(), parent).getContacted());
    }

    private String login(String role) {
        User u = new User(UUID.randomUUID(), "u", "hash", role, "ACTIVE", null, null);
        when(users.findByPrincipal(u.getId().toString())).thenReturn(Optional.of(u));
        return u.getId().toString();
    }

    private CustomizeRequest saved(UUID parentId, String status) {
        CustomizeRequest r = CustomizeRequest.builder().id(UUID.randomUUID()).parentId(parentId).title("Vẽ mèo").status(status).build();
        when(repo.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    private UpdateCustomizeRequestRequest edit() {
        UpdateCustomizeRequestRequest req = new UpdateCustomizeRequestRequest();
        req.setTitle("Vẽ mèo (đã sửa)");
        return req;
    }

    @Test
    void newRequestWaitsForStaffReview() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        CreateCustomizeRequestRequest req = new CreateCustomizeRequestRequest();
        req.setTitle("Vẽ phong cảnh");

        var created = service.create(req, login("PARENT"));

        assertEquals("PENDING_REVIEW", created.getStatus());
        assertEquals(0, created.getRejectionCount());
    }

    @Test
    void teacherCanListOnlyOpenRequests_adminAny_andLegacyPendingMeansOpen() {
        String teacher = login("TEACHER");
        String admin = login("ADMIN");
        when(repo.findAllByStatus(any())).thenReturn(List.of());

        service.getByStatus("pending", teacher);
        service.getByStatus("open", teacher);
        assertThrows(ResponseStatusException.class, () -> service.getByStatus("PENDING_REVIEW", teacher));
        assertThrows(ResponseStatusException.class, () -> service.getByStatus("accepted", teacher));
        service.getByStatus("pending_review", admin);
        service.getByStatus("accepted", admin);

        verify(repo, times(2)).findAllByStatus("OPEN");
        verify(repo).findAllByStatus("PENDING_REVIEW");
        verify(repo).findAllByStatus("ACCEPTED");
        assertThrows(ResponseStatusException.class, () -> service.getByStatus("DONE", admin));
    }

    @Test
    void teacherReadsOpenRequestOrOneTheyOfferedOn_notUnreviewedOnes() {
        String teacher = login("TEACHER");
        UUID teacherId = UUID.fromString(teacher);
        CustomizeRequest open = saved(UUID.randomUUID(), "OPEN");
        CustomizeRequest pending = saved(UUID.randomUUID(), "PENDING_REVIEW");
        CustomizeRequest accepted = saved(UUID.randomUUID(), "FUNDED");
        when(offers.findAllByRequestIdAndTeacherId(accepted.getId(), teacherId))
                .thenReturn(List.of(Offer.builder().teacherId(teacherId).status("accepted").build()));
        when(offers.findAllByRequestIdAndTeacherId(pending.getId(), teacherId)).thenReturn(List.of());

        assertEquals("OPEN", service.getById(open.getId(), teacher).getStatus());
        assertEquals("FUNDED", service.getById(accepted.getId(), teacher).getStatus());
        assertThrows(ResponseStatusException.class, () -> service.getById(pending.getId(), teacher));
    }

    @Test
    void teacherSeesOnlyOpenOfAParent_ownerSeesAll() {
        UUID parentId = UUID.randomUUID();
        when(repo.findAllByParentId(parentId)).thenReturn(List.of(
                CustomizeRequest.builder().id(UUID.randomUUID()).parentId(parentId).status("OPEN").build(),
                CustomizeRequest.builder().id(UUID.randomUUID()).parentId(parentId).status("PENDING_REVIEW").build()));

        assertEquals(1, service.getByParentId(parentId, login("TEACHER")).size());
        User owner = new User(parentId, "p", "h", "PARENT", "ACTIVE", null, null);
        when(users.findByPrincipal(parentId.toString())).thenReturn(Optional.of(owner));
        assertEquals(2, service.getByParentId(parentId, parentId.toString()).size());
    }

    @Test
    void editingAnOpenRequestClosesItUntilStaffApprovesAgain() {
        String parent = login("PARENT");
        CustomizeRequest open = saved(UUID.fromString(parent), "OPEN");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.update(open.getId(), edit(), parent);

        assertEquals("PENDING_REVIEW", result.getStatus());
        assertEquals("Vẽ mèo (đã sửa)", open.getTitle());
    }

    @Test
    void rejectedRequestCanBeResubmittedUntilThirdRejection() {
        String parent = login("PARENT");
        CustomizeRequest rejected = saved(UUID.fromString(parent), "REJECTED");
        rejected.setRejectionCount(2);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals("PENDING_REVIEW", service.update(rejected.getId(), edit(), parent).getStatus());

        CustomizeRequest finalRejected = saved(UUID.fromString(parent), "REJECTED");
        finalRejected.setRejectionCount(3);
        assertThrows(ResponseStatusException.class, () -> service.update(finalRejected.getId(), edit(), parent));
        assertEquals("REJECTED", finalRejected.getStatus());
    }

    @Test
    void requestWithAnAcceptedOfferCannotBeEditedOrDeleted_othersCan() {
        String parent = login("PARENT");
        UUID parentId = UUID.fromString(parent);
        CustomizeRequest accepted = saved(parentId, "ACCEPTED");

        assertThrows(ResponseStatusException.class, () -> service.update(accepted.getId(), edit(), parent));
        assertThrows(ResponseStatusException.class, () -> service.delete(accepted.getId(), parent));

        for (String status : List.of("OPEN", "PENDING_REVIEW", "REJECTED")) {
            CustomizeRequest r = saved(parentId, status);
            service.delete(r.getId(), parent);
            verify(repo).delete(r);
        }
    }

    @Test
    void ownerClosesAnUnstartedRequest_butNotOneWithAnAcceptedOffer() {
        String parent = login("PARENT");
        UUID parentId = UUID.fromString(parent);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        for (String status : List.of("OPEN", "PENDING_REVIEW", "REJECTED")) {
            CustomizeRequest r = saved(parentId, status);
            assertEquals("CANCELLED", service.cancel(r.getId(), parent).getStatus());
        }
        CustomizeRequest accepted = saved(parentId, "ACCEPTED");
        assertThrows(ResponseStatusException.class, () -> service.cancel(accepted.getId(), parent));
        CustomizeRequest others = saved(UUID.randomUUID(), "OPEN");
        assertThrows(ResponseStatusException.class, () -> service.cancel(others.getId(), parent));
        assertEquals("OPEN", others.getStatus());
    }

    @Test
    void strangerCannotEditOthersRequest() {
        CustomizeRequest open = saved(UUID.randomUUID(), "OPEN");
        assertThrows(ResponseStatusException.class, () -> service.update(open.getId(), edit(), login("PARENT")));
        assertEquals("OPEN", open.getStatus());
    }

    @Test
    void onlyAdminCanSetStatusByHand() {
        CustomizeRequest open = saved(UUID.randomUUID(), "OPEN");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThrows(ResponseStatusException.class, () -> service.updateStatus(open.getId(), "FUNDED", login("TEACHER")));
        assertEquals("OPEN", open.getStatus());
        assertEquals("CANCELLED", service.updateStatus(open.getId(), "cancelled", login("ADMIN")).getStatus());
    }
}
