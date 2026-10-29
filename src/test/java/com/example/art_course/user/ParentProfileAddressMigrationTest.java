package com.example.art_course.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

// SHOP-06: kiểm tra chuyển địa chỉ và rollback trên PostgreSQL cô lập.
@Testcontainers(disabledWithoutDocker = true)
class ParentProfileAddressMigrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private String migration() throws Exception {
        try (var in = getClass().getResourceAsStream("/db/migration/V10__parent_profile_address.sql")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void schema() throws Exception {
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS address_uses;
                    DROP TABLE IF EXISTS parent_addresses;
                    DROP TABLE IF EXISTS parent_profiles;
                    CREATE TABLE parent_profiles (
                        user_id int PRIMARY KEY, full_name text, shipping_address text, phone text);
                    CREATE TABLE parent_addresses (
                        id int PRIMARY KEY, parent_id int, address_line text, phone text, is_default boolean);
                    INSERT INTO parent_profiles VALUES
                        (1, 'Parent 1', '  ', ''), (2, 'Parent 2', 'Existing address', 'Existing phone');
                    INSERT INTO parent_addresses VALUES
                        (1, 1, ' Default address ', '0901234567', true),
                        (2, 1, 'Other address', '0912345678', false),
                        (3, 2, 'Old address', '0923456789', true);
                    """);
        }
    }

    @Test
    void copiesDefaultPreservesExistingProfileDropsTableAndCanRunAgain() throws Exception {
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute(migration());
            try (var rows = statement.executeQuery("SELECT * FROM parent_profiles ORDER BY user_id")) {
                assertTrue(rows.next());
                assertEquals("Parent 1", rows.getString("full_name"));
                assertEquals("Default address", rows.getString("shipping_address"));
                assertEquals("0901234567", rows.getString("phone"));
                assertTrue(rows.next());
                assertEquals("Existing address", rows.getString("shipping_address"));
                assertEquals("Existing phone", rows.getString("phone"));
            }
            try (var rows = statement.executeQuery("SELECT to_regclass('public.parent_addresses')")) {
                assertTrue(rows.next());
                assertNull(rows.getString(1));
            }
            statement.execute(migration());
        }
    }

    @Test
    void missingProfileOrDefaultStopsBeforeDroppingData() throws Exception {
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM parent_profiles WHERE user_id = 1");
            assertThrows(SQLException.class, () -> statement.execute(migration()));
            statement.executeUpdate("INSERT INTO parent_profiles VALUES (1, 'Parent 1', NULL, NULL)");
            statement.executeUpdate("UPDATE parent_addresses SET is_default = false WHERE parent_id = 1");
            assertThrows(SQLException.class, () -> statement.execute(migration()));
            try (var rows = statement.executeQuery("SELECT count(*) FROM parent_addresses")) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
            }
        }
    }

    @Test
    void dependentTableBlocksDropAndRollsBackCopiedAddress() throws Exception {
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE address_uses (address_id int REFERENCES parent_addresses(id))");
            assertThrows(SQLException.class, () -> statement.execute(migration()));
            try (var rows = statement.executeQuery("SELECT shipping_address FROM parent_profiles WHERE user_id = 1")) {
                assertTrue(rows.next());
                assertEquals("  ", rows.getString(1));
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM parent_addresses")) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
            }
        }
    }
}
