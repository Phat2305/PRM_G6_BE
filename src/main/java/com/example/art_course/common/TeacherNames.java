package com.example.art_course.common;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Tra họ tên giáo viên/phụ huynh (teacher_profiles, parent_profiles .full_name, chỉ đọc) để gắn vào API.
@Component
@RequiredArgsConstructor
public class TeacherNames {

    private final EntityManager entityManager;

    public Map<UUID, String> byIds(Collection<UUID> ids) {
        return namesFrom("teacher_profiles", ids);
    }

    // Cùng cách tra cho phụ huynh (parent_profiles.full_name), dùng cho màn Staff xem hoàn tiền.
    public Map<UUID, String> parentsByIds(Collection<UUID> ids) {
        return namesFrom("parent_profiles", ids);
    }

    // table chỉ nhận hằng số nội bộ ở trên, không bao giờ từ input người dùng.
    private Map<UUID, String> namesFrom(String table, Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = entityManager
                .createNativeQuery("SELECT user_id, full_name FROM " + table + " WHERE user_id IN (:ids)")
                .setParameter("ids", ids)
                .getResultList();
        for (Object[] row : rows) {
            names.put((UUID) row[0], (String) row[1]);
        }
        return names;
    }

    // Giáo viên đang hoạt động chưa đặt phí ship riêng, tức đơn của họ tính theo mức mặc định của sàn.
    public java.util.List<UUID> teacherIdsOnDefaultShippingFee() {
        @SuppressWarnings("unchecked")
        java.util.List<Object> rows = entityManager
                .createNativeQuery("SELECT p.user_id FROM teacher_profiles p JOIN users u ON u.id = p.user_id "
                        + "WHERE p.shipping_fee IS NULL AND u.status = 'ACTIVE'")
                .getResultList();
        return rows.stream().map(r -> (UUID) r).toList();
    }

    // Phí ship riêng của giáo viên (teacher_profiles.shipping_fee); null = dùng mặc định trong system_config.
    public Long shippingFeeOf(UUID teacherId) {
        if (teacherId == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        java.util.List<Object> rows = entityManager
                .createNativeQuery("SELECT shipping_fee FROM teacher_profiles WHERE user_id = :id")
                .setParameter("id", teacherId)
                .getResultList();
        return rows.isEmpty() || rows.get(0) == null ? null : ((Number) rows.get(0)).longValue();
    }

    public String byId(UUID id) {
        return id == null ? null : byIds(java.util.List.of(id)).get(id);
    }
}
