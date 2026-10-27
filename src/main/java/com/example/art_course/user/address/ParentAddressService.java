package com.example.art_course.user.address;

import com.example.art_course.common.AppError;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParentAddressService {

    static final int MAX_ADDRESSES = 10;

    private final ParentAddressRepository repository;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID parentId) {
        return repository.findAllByParentIdOrderByIsDefaultDescCreatedAtDesc(parentId).stream()
                .map(AddressResponse::of).toList();
    }

    @Transactional
    public AddressResponse create(UUID parentId, AddressRequest request) {
        long count = repository.countByParentId(parentId);
        if (count >= MAX_ADDRESSES) {
            throw new AppError(HttpStatus.CONFLICT, "TOO_MANY_ADDRESSES", "Tối đa " + MAX_ADDRESSES + " địa chỉ");
        }
        boolean makeDefault = count == 0 || Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            repository.clearDefault(parentId);
        }
        ParentAddress saved = repository.save(ParentAddress.builder()
                .parentId(parentId)
                .recipientName(request.getRecipientName().trim())
                .phone(request.getPhone())
                .addressLine(request.getAddressLine().trim())
                .isDefault(makeDefault)
                .build());
        return AddressResponse.of(saved);
    }

    @Transactional
    public AddressResponse update(UUID parentId, UUID id, AddressRequest request) {
        ParentAddress address = owned(parentId, id);
        boolean promote = Boolean.TRUE.equals(request.getIsDefault()) && !address.isDefault();
        if (promote) {
            repository.clearDefault(parentId);
            address = owned(parentId, id);
            address.setDefault(true);
        }
        address.setRecipientName(request.getRecipientName().trim());
        address.setPhone(request.getPhone());
        address.setAddressLine(request.getAddressLine().trim());
        return AddressResponse.of(repository.save(address));
    }

    @Transactional
    public AddressResponse setDefault(UUID parentId, UUID id) {
        ParentAddress address = owned(parentId, id);
        if (!address.isDefault()) {
            repository.clearDefault(parentId);
            address = owned(parentId, id);
            address.setDefault(true);
            repository.save(address);
        }
        return AddressResponse.of(address);
    }

    // Phải còn ít nhất một địa chỉ; xóa địa chỉ mặc định thì địa chỉ mới nhất còn lại thành mặc định.
    @Transactional
    public void delete(UUID parentId, UUID id) {
        ParentAddress address = owned(parentId, id);
        List<ParentAddress> all = repository.findAllByParentIdOrderByIsDefaultDescCreatedAtDesc(parentId);
        if (all.size() <= 1) {
            throw new AppError(HttpStatus.CONFLICT, "LAST_ADDRESS", "Cần giữ ít nhất một địa chỉ giao hàng");
        }
        boolean wasDefault = address.isDefault();
        repository.delete(address);
        repository.flush();
        if (wasDefault) {
            all.stream().filter(a -> !a.getId().equals(id)).findFirst().ifPresent(next -> {
                next.setDefault(true);
                repository.save(next);
            });
        }
    }

    private ParentAddress owned(UUID parentId, UUID id) {
        ParentAddress address = repository.findById(id)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "Không tìm thấy địa chỉ"));
        if (!address.getParentId().equals(parentId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Địa chỉ này không thuộc về bạn");
        }
        return address;
    }
}
