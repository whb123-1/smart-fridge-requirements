package com.xianzhi.fridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class UsernameMigrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("username_migration")
            .withUsername("xianzhi")
            .withPassword("test-password");

    @Test
    void migrationPreservesEligibleDisplayNameAndFallsBackToUuid() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("2")
                .load()
                .migrate();

        UUID preservedId = UUID.fromString("0198b641-6321-7000-8000-000000000001");
        UUID fallbackId = UUID.fromString("0198b641-6321-7000-8000-000000000002");
        try (Connection connection = connection()) {
            insertLegacyUser(connection, preservedId, "preserved@example.com", "Legacy_User");
            insertLegacyUser(connection, fallbackId, "fallback@example.com", "Legacy User");
        }

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();

        try (Connection connection = connection()) {
            assertThat(usernameFor(connection, "preserved@example.com")).isEqualTo("legacy_user");
            assertThat(usernameFor(connection, "fallback@example.com"))
                    .isEqualTo(fallbackId.toString().replace("-", ""));
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static void insertLegacyUser(Connection connection, UUID id, String email, String displayName)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_user
                  (id, email, password_hash, display_name, timezone, temperature_unit, status,
                   created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, 'hash', ?, 'Asia/Shanghai', 'C', 'ACTIVE',
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0)
                """)) {
            statement.setString(1, id.toString());
            statement.setString(2, email);
            statement.setString(3, displayName);
            statement.executeUpdate();
        }
    }

    private static String usernameFor(Connection connection, String email) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT username FROM app_user WHERE email = ?")) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }
}
