
package com.example.art_course.supply.Controller;

import com.example.art_course.supply.dto.CreateSupplyRequest;
import com.example.art_course.supply.dto.UpdateSupplyRequest;
import com.example.art_course.supply.dto.SupplyResponse;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.service.SupplyService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplies")
public class SupplyController {

    private final SupplyService supplyService;

    public SupplyController(SupplyService supplyService) {
        this.supplyService = supplyService;
    }

    @GetMapping
    public List<SupplyResponse> getAllSupplies() {
        return supplyService.getAllSupplies()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public SupplyResponse getSupplyById(@PathVariable UUID id) {
        return toResponse(supplyService.getSupplyById(id));
    }

    @GetMapping("/teacher/{teacherId}")
    public List<SupplyResponse> getSuppliesByTeacher(
            @PathVariable UUID teacherId) {

        return supplyService.getSuppliesByTeacher(teacherId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // CRS-06: chỉ Teacher được tạo dụng cụ (Parent/Child/Staff nhận 403).
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplyResponse createSupply(
            @Valid @RequestBody CreateSupplyRequest request,
            Authentication authentication) {

        return toResponse(
                supplyService.createSupply(
                        request,
                        authentication.getName()
                )
        );
    }

    @PutMapping("/{id}")
    public SupplyResponse updateSupply(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSupplyRequest request,
            Authentication authentication) {

        return toResponse(
                supplyService.updateSupply(
                        id,
                        request,
                        authentication.getName()
                )
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSupply(
            @PathVariable UUID id,
            Authentication authentication) {

        supplyService.deleteSupply(id, authentication.getName());
    }

    private SupplyResponse toResponse(Supply supply) {
        return new SupplyResponse(
                supply.getId(),
                supply.getTeacherId(),
                supply.getTeacherName(),
                supply.getName(),
                supply.getDescription(),
                supply.getImageUrl(),
                supply.getPrice(),
                supply.getStockQuantity(),
                supply.getStatus()
        );
    }
}
