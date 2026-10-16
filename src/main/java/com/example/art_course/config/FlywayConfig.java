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
        String cleanUrl = databaseUrlDev.contains("?") ? databaseUrlDev.substring(0, databaseUrlDev.indexOf("?")) : databaseUrlDev.trim();
        int lastSlashIndex = cleanUrl.lastIndexOf("/");
        String dbName = (lastSlashIndex >= 0) ? cleanUrl.substring(lastSlashIndex + 1).trim() : cleanUrl;

        if (!dbName.toLowerCase().endsWith("_dev")) {
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
}
