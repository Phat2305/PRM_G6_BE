package com.example.art_course.user;

import com.example.art_course.common.AppError;
import com.example.art_course.user.address.AddressRequest;
import com.example.art_course.user.address.ParentAddress;
import com.example.art_course.user.address.ParentAddressRepository;
import com.example.art_course.user.address.ParentAddressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Sổ địa chỉ: địa chỉ đầu tiên là mặc định, không xóa cái cuối, chỉ chủ sở hữu được sửa")
class ParentAddressServiceTest {

    private final ParentAddressRepository repo = mock(ParentAddressRepository.class);
    private final ParentAddressService service = new ParentAddressService(repo);
    private final UUID parentId = UUID.randomUUID();

    private AddressRequest request(Boolean isDefault) {
        AddressRequest r = new AddressRequest();
        r.setRecipientName(" Chị Mai ");
        r.setPhone("0901234567");
        r.setAddressLine(" 12 Nguyễn Trãi ");
        r.setIsDefault(isDefault);
        return r;
    }

    private ParentAddress address(UUID owner, boolean isDefault) {
        return ParentAddress.builder().id(UUID.randomUUID()).parentId(owner).recipientName("A")
                .phone("0901234567").addressLine("x").isDefault(isDefault).build();
    }

    @Test
    @DisplayName("địa chỉ đầu tiên tự thành mặc định; địa chỉ sau chỉ mặc định khi yêu cầu")
    void firstAddressIsDefault() {
        when(repo.save(any(ParentAddress.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo.countByParentId(parentId)).thenReturn(0L);
        var first = service.create(parentId, request(null));
        assertTrue(first.isDefault());
        assertEquals("Chị Mai", first.recipientName());

        when(repo.countByParentId(parentId)).thenReturn(1L);
        assertFalse(service.create(parentId, request(null)).isDefault());
        assertTrue(service.create(parentId, request(true)).isDefault());
        verify(repo, times(2)).clearDefault(parentId);
    }

    @Test
    @DisplayName("xóa: địa chỉ cuối bị từ chối; xóa địa chỉ mặc định thì chuyển mặc định sang địa chỉ còn lại")
    void deleteKeepsOneAndPromotes() {
        ParentAddress def = address(parentId, true);
        ParentAddress other = address(parentId, false);
        when(repo.findById(def.getId())).thenReturn(Optional.of(def));
        when(repo.findAllByParentIdOrderByIsDefaultDescCreatedAtDesc(parentId)).thenReturn(List.of(def));
        AppError last = assertThrows(AppError.class, () -> service.delete(parentId, def.getId()));
        assertEquals("LAST_ADDRESS", last.getErrorCode());

        when(repo.findAllByParentIdOrderByIsDefaultDescCreatedAtDesc(parentId)).thenReturn(List.of(def, other));
        service.delete(parentId, def.getId());
        verify(repo).delete(def);
        assertTrue(other.isDefault());
    }

    @Test
    @DisplayName("sửa/xóa địa chỉ của người khác → 403")
    void foreignAddressIsForbidden() {
        ParentAddress foreign = address(UUID.randomUUID(), false);
        when(repo.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        assertEquals(403, assertThrows(AppError.class,
                () -> service.update(parentId, foreign.getId(), request(null))).getStatus().value());
        assertEquals(403, assertThrows(AppError.class,
                () -> service.setDefault(parentId, foreign.getId())).getStatus().value());
    }
}
