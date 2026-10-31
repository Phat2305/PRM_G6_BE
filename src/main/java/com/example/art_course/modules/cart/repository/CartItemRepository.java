package com.example.art_course.modules.cart.repository;

import com.example.art_course.modules.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartId(UUID cartId);
    Optional<CartItem> findByCartIdAndCourseIdAndChildId(UUID cartId, UUID courseId, UUID childId);
    boolean existsByCartIdAndCourseId(UUID cartId, UUID courseId);
    Optional<CartItem> findByCartIdAndSupplyId(UUID cartId, UUID supplyId);
    void deleteByCartId(UUID cartId);
}
