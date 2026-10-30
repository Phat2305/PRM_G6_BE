
package com.example.art_course.supply.service;

import com.example.art_course.supply.dto.CreateSupplyRequest;
import com.example.art_course.common.TeacherNames;
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
import java.util.Map;
import java.util.UUID;

@Service
public class SupplyService {

    private final SupplyRepository supplyRepository;
    private final UserRepository userRepository;
    private final TeacherNames teacherNames;

    public SupplyService(
            SupplyRepository supplyRepository,
            UserRepository userRepository,
            TeacherNames teacherNames) {
        this.supplyRepository = supplyRepository;
        this.userRepository = userRepository;
        this.teacherNames = teacherNames;
    }

    @Transactional(readOnly = true)
    public List<Supply> getAllSupplies() {
        // STF-01: tra họ tên một lần cho danh sách dụng cụ chờ kiểm duyệt.
        List<Supply> supplies = supplyRepository.findAll();
        Map<UUID, String> names = teacherNames.byIds(supplies.stream().map(Supply::getTeacherId).distinct().toList());
        supplies.forEach(supply -> supply.setTeacherName(names.get(supply.getTeacherId())));
        return supplies;
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
            String userId) {

        if (request.getName() == null
                || request.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supply name is required"
            );
        }

        User user = getUserById(userId);
        Supply supply = Supply.builder()
                // Use the authenticated user's ID.
                .teacherId(user.getId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                // CRS-06: client status is ignored; a new supply always waits for Staff approval
                .status("PENDING_REVIEW")
                .build();

        return supplyRepository.save(supply);
    }

    @Transactional
    public Supply updateSupply(
            UUID id,
            UpdateSupplyRequest request,
            String userId) {

        Supply supply = getSupplyById(id);
        User user = getUserById(userId);
        checkOwnership(supply, user);

        boolean contentChanged = false;

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Supply name cannot be blank"
                );
            }

            String name = request.getName().trim();
            contentChanged |= !name.equals(supply.getName());
            supply.setName(name);
        }

        if (request.getDescription() != null) {
            contentChanged |= !request.getDescription().equals(blankIfNull(supply.getDescription()));
            supply.setDescription(request.getDescription());
        }

        if (request.getImageUrl() != null) {
            contentChanged |= !request.getImageUrl().equals(blankIfNull(supply.getImageUrl()));
            supply.setImageUrl(request.getImageUrl());
        }

        if (request.getPrice() != null) {
            contentChanged |= supply.getPrice() == null
                    || supply.getPrice().compareTo(request.getPrice()) != 0;
            supply.setPrice(request.getPrice());
        }

        if (request.getStockQuantity() != null) {
            supply.setStockQuantity(request.getStockQuantity());
        }

        // CRS-06/07: client status is ignored. Changing what the buyer sees goes back to Staff review;
        // a stock-only change never needs review, it just toggles ACTIVE <-> OUT_OF_STOCK.
        if (contentChanged) {
            supply.setStatus("PENDING_REVIEW");
        } else {
            supply.syncStatusWithStock();
        }

        return supplyRepository.save(supply);
    }

    @Transactional
    public void deleteSupply(UUID id, String userId) {
        Supply supply = getSupplyById(id);
        User user = getUserById(userId);
        checkOwnership(supply, user);

        supplyRepository.delete(supply);
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private User getUserById(String userId) {
        try {
            UUID id = UUID.fromString(userId);

            return userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "User not found"
                    ));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user ID"
            );
        }
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
