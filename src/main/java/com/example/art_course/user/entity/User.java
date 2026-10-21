package com.example.art_course.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "role")
    private String role;

    @Column(name = "status")
    private String status;

    // Lưu chữ thường; dùng cho quên mật khẩu. Tài khoản cũ có thể chưa có (null).
    @Column(name = "email")
    private String email;

    // Token cấp trước mốc này (iat <= mốc) bị từ chối. null = chưa thu hồi lần nào.
    @Column(name = "tokens_valid_after")
    private Instant tokensValidAfter;

    // Thu hồi mọi token đã cấp tới giờ; chỗ duy nhất đặt mốc này
    public void revokeTokens() {
        this.tokensValidAfter = Instant.now();
    }
}
