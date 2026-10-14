
package com.example.art_course.supply.service;

import com.example.art_course.supply.dto.CreateSupplyRequest;
import com.example.art_course.supply.dto.UpdateSupplyRequest;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SupplyService {

    private final SupplyRepository supplyRepository;

    public SupplyService(SupplyRepository supplyRepository) {
        this.supplyRepository = supplyRepository;
    }

    @Transactional(readOnly = true)
    public List<Supply> getAllSupplies() {
        return supplyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Supply getSupplyById(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supply ID is required"
            );
        }

        return supplyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supply not found"
                ));
    }

    @Transactional(readOnly = true)
    public List<Supply> getSuppliesByTeacher(UUID teacherId) {
        return supplyRepository.findAllByTeacherId(teacherId);
    }

    @Transactional
    public Supply createSupply(CreateSupplyRequest request) {
        if (request.getName() == null
                || request.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supply name is required"
            );
        }

        Supply supply = Supply.builder()
                .teacherId(request.getTeacherId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .status(request.getStatus())
                .build();

        return supplyRepository.save(supply);
    }

    @Transactional
    public Supply updateSupply(
            UUID id,
            UpdateSupplyRequest request
    ) {
        Supply supply = getSupplyById(id);

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Supply name cannot be blank"
                );
            }
            supply.setName(request.getName().trim());
        }

        if (request.getDescription() != null) {
            supply.setDescription(request.getDescription());
        }

        if (request.getImageUrl() != null) {
            supply.setImageUrl(request.getImageUrl());
        }

        if (request.getPrice() != null) {
            supply.setPrice(request.getPrice());
        }

        if (request.getStockQuantity() != null) {
            supply.setStockQuantity(request.getStockQuantity());
        }

        if (request.getStatus() != null) {
            supply.setStatus(request.getStatus());
        }

        return supplyRepository.save(supply);
    }

    @Transactional
    public void deleteSupply(UUID id) {
        Supply supply = getSupplyById(id);
        supplyRepository.delete(supply);
    }
}
