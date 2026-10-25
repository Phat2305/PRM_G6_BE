package com.example.art_course.user.repository;

import com.example.art_course.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    List<User> findAllByUsernameIgnoreCase(String username);
    // Username coi như không phân biệt hoa/thường; UNIQUE trong DB vẫn phân biệt nên kiểm tra ở đây.
    boolean existsByUsernameIgnoreCase(String username);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // Đăng nhập: khớp đúng chữ trước; nếu không thì chỉ nhận khi đúng một tài khoản khớp không phân biệt hoa/thường
    // (DB cũ có thể còn "An" và "an" là hai tài khoản khác nhau).
    default Optional<User> findForLogin(String username) {
        Optional<User> exact = findByUsername(username);
        if (exact.isPresent()) {
            return exact;
        }
        List<User> matches = findAllByUsernameIgnoreCase(username);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    // authentication.getName() là UUID người dùng (subject của JWT); vẫn nhận username cho token cũ/test.
    default Optional<User> findByPrincipal(String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            return findById(UUID.fromString(name));
        } catch (IllegalArgumentException e) {
            return findByUsername(name);
        }
    }

    // Đổi trạng thái giáo viên đang PENDING bằng một câu lệnh nguyên tử: hai người duyệt cùng lúc thì chỉ một người thắng (trả 0 cho người còn lại).
    @Modifying(clearAutomatically = true)
    @Query("update User u set u.status = :to where u.id = :id and u.role = 'TEACHER' and u.status = 'PENDING'")
    int decidePendingTeacher(@Param("id") UUID id, @Param("to") String to);

    // Trạng thái tài khoản và mốc thu hồi token, JwtAuthFilter tra ở mỗi request
    interface AuthState {
        String getStatus();
        Instant getTokensValidAfter();
    }

    @Query("select u.status as status, u.tokensValidAfter as tokensValidAfter from User u where u.id = :id")
    Optional<AuthState> findAuthStateById(@Param("id") UUID id);
}
