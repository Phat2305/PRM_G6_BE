package com.example.art_course.common;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Tra họ tên giáo viên (teacher_profiles.full_name, bảng của BE1, chỉ đọc) để gắn vào API mua sắm.
@Component
@RequiredArgsConstructor
public class TeacherNames {

    private final EntityManager entityManager;

    public Map<UUID, String> byIds(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = entityManager
                .createNativeQuery("SELECT user_id, full_name FROM teacher_profiles WHERE user_id IN (:ids)")
                .setParameter("ids", ids)
                .getResultList();
        for (Object[] row : rows) {
            names.put((UUID) row[0], (String) row[1]);
        }
        return names;
    }

    public String byId(UUID id) {
        return id == null ? null : byIds(java.util.List.of(id)).get(id);
    }
}
