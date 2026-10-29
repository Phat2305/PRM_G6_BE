package com.example.art_course.customize_request;

import com.example.art_course.customize_request.repository.CustomizeRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

// Executes the repository's actual SQL on an isolated database; does not load app config or migrations.
@Testcontainers(disabledWithoutDocker = true)
class CustomizeContactCountTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void countsUniqueTeachersAcrossQuotesAndChatsWithoutMixingOtherRequestsOrLessons() throws Exception {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE offers (request_id uuid, teacher_id uuid)");
        jdbc.execute("CREATE TABLE conversations (context_id uuid, teacher_id uuid, context_type text)");
        UUID request = UUID.randomUUID(), other = UUID.randomUUID(), empty = UUID.randomUUID();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        jdbc.update("INSERT INTO offers VALUES (?, ?), (?, ?), (?, ?)", request, first, request, first, other, third);
        jdbc.update("INSERT INTO conversations VALUES (?, ?, 'CUSTOMIZE_REQUEST'), (?, ?, 'customize_request'), (?, ?, 'LESSON')",
                request, first, request, second, request, third);
        String sql = CustomizeRequestRepository.class.getMethod("countTeacherContacts", Collection.class)
                .getAnnotation(Query.class).value();
        var rows = new NamedParameterJdbcTemplate(dataSource)
                .queryForList(sql, Map.of("requestIds", List.of(request, other, empty)));
        Map<UUID, Long> counts = new HashMap<>();
        for (var row : rows) counts.put((UUID) row.get("requestId"), ((Number) row.get("contacted")).longValue());
        assertEquals(Map.of(request, 2L, other, 1L), counts);
        assertFalse(counts.containsKey(empty));
    }
}
