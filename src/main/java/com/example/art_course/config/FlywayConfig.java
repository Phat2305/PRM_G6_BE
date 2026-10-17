package com.example.art_course.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Cấu hình Flyway cho BE2:
 * 1. Mặc định Flyway TẮT (ở profile default).
 * 2. Chỉ kích hoạt ở profile 'dev'.
 * 3. CHỈ chạy migration khi biến DATABASE_URL_DEV được đặt giá trị cụ thể.
 * 4. Không bao giờ chạy trên môi trường Supabase production.
 */
@Slf4j
@Configuration
@Profile("dev")
public class FlywayConfig {

    @Value("${DATABASE_URL_DEV:}")
    private String databaseUrlDev;

    @Value("${spring.flyway.enabled:false}")
    private boolean flywayEnabled;

    @Bean
    public Flyway flyway(DataSource dataSource) {
        if (!flywayEnabled) {
            log.info("[FLYWAY] spring.flyway.enabled=false -> Bỏ qua Flyway migration.");
            return null;
        }

        if (databaseUrlDev == null || databaseUrlDev.trim().isEmpty()) {
            log.warn("[FLYWAY] DATABASE_URL_DEV chưa được đặt -> Bỏ qua Flyway migration để bảo vệ an toàn.");
            return null;
        }

        // BẢO VỆ AN TOÀN BẤT BIẾN: Kiểm tra tên database trong JDBC URL phải kết thúc bằng '_dev'
        String dbName = extractDatabaseName(databaseUrlDev);

        if (!isAllowedDevDatabaseUrl(databaseUrlDev)) {
            log.error("[FLYWAY-GUARD] LỖI NGUY HIỂM: Tên database '{}' trong DATABASE_URL_DEV không kết thúc bằng '_dev' (đặc biệt cấm chạy trên 'art_course' hay production)! Từ chối khởi chạy Flyway.", dbName);
            return null;
        }

        log.info("[FLYWAY] Đang khởi chạy Flyway migration trên profile dev cho database hợp lệ '{}'...", dbName);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        flyway.migrate();
        log.info("[FLYWAY] Flyway migration cho '{}' đã hoàn tất.", dbName);
        return flyway;
    }

    public static String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            return "";
        }
        String cleanUrl = jdbcUrl.contains("?") ? jdbcUrl.substring(0, jdbcUrl.indexOf("?")) : jdbcUrl.trim();
        int lastSlashIndex = cleanUrl.lastIndexOf("/");
        return (lastSlashIndex >= 0) ? cleanUrl.substring(lastSlashIndex + 1).trim() : cleanUrl;
    }

    public static boolean isAllowedDevDatabaseUrl(String jdbcUrl) {
        String dbName = extractDatabaseName(jdbcUrl);
        if (dbName.isEmpty()) {
            return false;
        }
        if ("art_course".equalsIgnoreCase(dbName) || "postgres".equalsIgnoreCase(dbName)) {
            return false;
        }
        return dbName.toLowerCase().endsWith("_dev");
    }

    @Bean
    public static org.springframework.beans.factory.config.BeanFactoryPostProcessor flywayEntityManagerDependencyPostProcessor() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory") && beanFactory.containsBeanDefinition("flyway")) {
                org.springframework.beans.factory.config.BeanDefinition bd = beanFactory.getBeanDefinition("entityManagerFactory");
                bd.setDependsOn("flyway");
            }
        };
    }
}
