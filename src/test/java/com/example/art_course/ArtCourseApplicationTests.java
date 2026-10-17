package com.example.art_course;

import com.example.art_course.common.PostgresTestcontainerEnv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtCourseApplicationTests {

    @Test
    @DisplayName("Kiểm chứng Testcontainers postgres:16-alpine và Safety Guard từ chối mọi JDBC URL ngoài container")
    void contextLoads() {
        var container = PostgresTestcontainerEnv.getContainer();
        assertTrue(container.isRunning(), "Testcontainers postgres:16-alpine phải đang chạy");
        assertDoesNotThrow(() -> PostgresTestcontainerEnv.assertIsolatedContainerUrl(container.getJdbcUrl()));

        // Guard bắt buộc từ chối mọi JDBC URL không phải của chính container đang chạy
        assertThrows(SecurityException.class, () ->
                PostgresTestcontainerEnv.assertIsolatedContainerUrl("jdbc:postgresql://localhost:5432/art_course_dev"));
        assertThrows(SecurityException.class, () ->
                PostgresTestcontainerEnv.assertIsolatedContainerUrl("jdbc:postgresql://localhost:5432/art_course"));
    }
}
