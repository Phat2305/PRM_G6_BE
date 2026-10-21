package com.example.art_course.user.repository;

import com.example.art_course.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // Trạng thái tài khoản và mốc thu hồi token, JwtAuthFilter tra ở mỗi request
    interface AuthState {
        String getStatus();
        Instant getTokensValidAfter();
    }

    @Query("select u.status as status, u.tokensValidAfter as tokensValidAfter from User u where u.id = :id")
    Optional<AuthState> findAuthStateById(@Param("id") UUID id);
}
