
package com.example.art_course.supply.service;

import com.example.art_course.supply.dto.CreateSupplyRequest;
import com.example.art_course.supply.dto.UpdateSupplyRequest;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SupplyService {

    private final SupplyRepository supplyRepository;
    private final UserRepository userRepository;

    public SupplyService(
            SupplyRepository supplyRepository,
            UserRepository userRepository) {
        this.supplyRepository = supplyRepository;
        this.userRepository = userRepository;
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
        if (teacherId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Teacher ID is required"
            );
        }

        return supplyRepository.findAllByTeacherId(teacherId);
    }

    @Transactional
    public Supply createSupply(
            CreateSupplyRequest request,
            String username) {

        if (request.getName() == null
                || request.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supply name is required"
            );
        }

        User user = getUserByUsername(username);

        Supply supply = Supply.builder()
                // Use the authenticated user's ID.
                .teacherId(user.getId())
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
            UpdateSupplyRequest request,
            String username) {

        Supply supply = getSupplyById(id);
        User user = getUserByUsername(username);

        checkOwnership(supply, user);

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
    public void deleteSupply(UUID id, String username) {
        Supply supply = getSupplyById(id);
        User user = getUserByUsername(username);

        checkOwnership(supply, user);

        supplyRepository.delete(supply);
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));
    }

    private void checkOwnership(Supply supply, User user) {
        if (supply.getTeacherId() == null
                || !supply.getTeacherId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to manage this supply"
            );
        }
    }
}
