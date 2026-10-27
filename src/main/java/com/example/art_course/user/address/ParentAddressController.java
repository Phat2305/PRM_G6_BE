package com.example.art_course.user.address;

import com.example.art_course.common.AppError;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Sổ địa chỉ của phụ huynh; SecurityConfig chỉ cho role PARENT.
@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
public class ParentAddressController {

    private final ParentAddressService service;

    @GetMapping
    public List<AddressResponse> list(Authentication auth) {
        return service.list(currentUserId(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@Valid @RequestBody AddressRequest request, Authentication auth) {
        return service.create(currentUserId(auth), request);
    }

    @PutMapping("/{id}")
    public AddressResponse update(@PathVariable UUID id, @Valid @RequestBody AddressRequest request,
                                  Authentication auth) {
        return service.update(currentUserId(auth), id, request);
    }

    @PatchMapping("/{id}/default")
    public AddressResponse setDefault(@PathVariable UUID id, Authentication auth) {
        return service.setDefault(currentUserId(auth), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        service.delete(currentUserId(auth), id);
    }

    private UUID currentUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UUID id)) {
            throw new AppError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token không hợp lệ");
        }
        return id;
    }
}
