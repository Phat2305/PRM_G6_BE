package com.example.art_course.auth.repository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Mã quên mật khẩu, mỗi email một mã đang hiệu lực.
// shortcut: lưu trong bộ nhớ nên mất khi restart và không chia sẻ giữa nhiều instance; đổi sang bảng DB khi chạy nhiều instance.
@Repository
public class PasswordResetRepository {

    public static class Entry {
        public final String codeHash;
        public final Instant sentAt;
        public final Instant expiresAt;
        public int failedAttempts;

        public Entry(String codeHash, Instant sentAt, Instant expiresAt) {
            this.codeHash = codeHash;
            this.sentAt = sentAt;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public Optional<Entry> find(String email) {
        return Optional.ofNullable(entries.get(email));
    }

    public void save(String email, Entry entry) {
        entries.put(email, entry);
    }

    public void delete(String email) {
        entries.remove(email);
    }
}
