package com.example.art_course.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// Truy cập DB cho bảng hồ sơ theo role (parent_profiles, teacher_profiles); tài khoản dùng UserRepository.
@Repository
@RequiredArgsConstructor
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    // Parent dùng shippingAddress, phone; Teacher dùng bankAccountInfo, phone, bio, certificatesText (mỗi dòng một chứng chỉ),
    // shippingFee (null = dùng phí ship mặc định trong system_config)
    public record ProfileRow(String fullName, String shippingAddress, String bankAccountInfo,
                             String phone, String bio, String certificatesText, Long shippingFee) {
        public ProfileRow(String fullName, String shippingAddress, String bankAccountInfo) {
            this(fullName, shippingAddress, bankAccountInfo, null, null, null, null);
        }

        public ProfileRow(String fullName, String shippingAddress, String bankAccountInfo,
                          String phone, String bio, String certificatesText) {
            this(fullName, shippingAddress, bankAccountInfo, phone, bio, certificatesText, null);
        }
    }

    // Teacher chưa có thông tin ngân hàng khi đăng ký (cột NOT NULL nên để rỗng)
    public void insertTeacherProfile(UUID userId, String fullName) {
        jdbcTemplate.update(
                "INSERT INTO teacher_profiles (user_id, full_name, bank_account_info) VALUES (?, ?, '')",
                userId, fullName);
    }

    public void insertParentProfile(UUID userId, String fullName) {
        jdbcTemplate.update(
                "INSERT INTO parent_profiles (user_id, full_name) VALUES (?, ?)",
                userId, fullName);
    }

    public Optional<ProfileRow> findParentProfile(UUID userId) {
        return jdbcTemplate.query(
                "SELECT full_name, shipping_address, phone FROM parent_profiles WHERE user_id = ?",
                (rs, i) -> new ProfileRow(rs.getString(1), rs.getString(2), null, rs.getString(3), null, null),
                userId)
                .stream().findFirst();
    }

    public Optional<ProfileRow> findTeacherProfile(UUID userId) {
        return jdbcTemplate.query(
                "SELECT full_name, bank_account_info, phone, bio, certificates_text, shipping_fee "
                        + "FROM teacher_profiles WHERE user_id = ?",
                (rs, i) -> new ProfileRow(rs.getString(1), null, rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getObject(6) == null ? null : rs.getLong(6)), userId)
                .stream().findFirst();
    }

    // Tham số null = giữ nguyên giá trị cũ; trả về số dòng được cập nhật
    public int updateParentProfile(UUID userId, String fullName, String shippingAddress, String phone) {
        return jdbcTemplate.update(
                "UPDATE parent_profiles SET full_name = COALESCE(?, full_name), "
                        + "shipping_address = COALESCE(?, shipping_address), "
                        + "phone = COALESCE(?, phone) WHERE user_id = ?",
                fullName, shippingAddress, phone, userId);
    }

    // Khóa R2 của file chứng chỉ, mỗi dòng một khóa, cùng thứ tự với certificates_text ("" = chưa có file).
    public String findCertificateKeys(UUID userId) {
        return jdbcTemplate.query(
                "SELECT certificate_keys FROM teacher_profiles WHERE user_id = ?",
                (rs, i) -> rs.getString(1), userId).stream().findFirst().orElse(null);
    }

    public void updateCertificateKeys(UUID userId, String keys) {
        jdbcTemplate.update("UPDATE teacher_profiles SET certificate_keys = ? WHERE user_id = ?", keys, userId);
    }

    // Chuỗi rỗng xóa giá trị (phone, bio, certificatesText); null giữ nguyên.
    public int updateTeacherProfile(UUID userId, String fullName, String bankAccountInfo,
                                    String phone, String bio, String certificatesText, Long shippingFee) {
        return jdbcTemplate.update(
                "UPDATE teacher_profiles SET full_name = COALESCE(?, full_name), "
                        + "bank_account_info = COALESCE(?, bank_account_info), "
                        + "phone = COALESCE(?, phone), bio = COALESCE(?, bio), "
                        + "certificates_text = COALESCE(?, certificates_text), "
                        + "shipping_fee = COALESCE(?, shipping_fee) WHERE user_id = ?",
                fullName, bankAccountInfo, phone, bio, certificatesText, shippingFee, userId);
    }
}
