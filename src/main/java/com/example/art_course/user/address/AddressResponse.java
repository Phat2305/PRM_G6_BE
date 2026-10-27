package com.example.art_course.user.address;

import java.util.UUID;

public record AddressResponse(UUID id, String recipientName, String phone, String addressLine, boolean isDefault) {

    static AddressResponse of(ParentAddress a) {
        return new AddressResponse(a.getId(), a.getRecipientName(), a.getPhone(), a.getAddressLine(), a.isDefault());
    }
}
