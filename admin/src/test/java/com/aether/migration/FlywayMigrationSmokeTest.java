package com.aether.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationSmokeTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
    void migratesAnEmptyPostgresDatabaseToLatestVersion() throws Exception {
        String url = System.getenv("FLYWAY_TEST_URL");
        String user = System.getenv("FLYWAY_TEST_USER");
        String password = System.getenv("FLYWAY_TEST_PASSWORD");
        String schema = uniqueSchema("flyway_empty_smoke_");
        createSchema(url, user, password, schema, false);
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(url, user, password)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration/postgresql")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .load();

            flyway.migrate();

            assertEquals("7", flyway.info().current().getVersion().getVersion());
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                try (ResultSet history = statement.executeQuery(
                        "SELECT COUNT(*) FROM " + schema + ".flyway_schema_history WHERE success")) {
                    assertTrue(history.next());
                    assertEquals(7, history.getInt(1));
                }
                try (ResultSet table = statement.executeQuery(
                        "SELECT to_regclass('" + schema + ".knowledge_review_task') IS NOT NULL")) {
                    assertTrue(table.next());
                    assertTrue(table.getBoolean(1));
                }
            }
        } finally {
            dropSchema(url, user, password, schema);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
    void baselinesAnExistingSchemaWithoutReplayingHistoricalMigrations() throws Exception {
        String url = System.getenv("FLYWAY_TEST_URL");
        String user = System.getenv("FLYWAY_TEST_USER");
        String password = System.getenv("FLYWAY_TEST_PASSWORD");
        String schema = uniqueSchema("flyway_baseline_smoke_");
        createSchema(url, user, password, schema, true);
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(url, user, password)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration/postgresql")
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .cleanDisabled(true)
                    .load();

            flyway.migrate();

            assertEquals("7", flyway.info().current().getVersion().getVersion());
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement();
                 ResultSet history = statement.executeQuery(
                         "SELECT COUNT(*) FROM " + schema
                                  + ".flyway_schema_history WHERE type = 'BASELINE' AND success")) {
                assertTrue(history.next());
                assertEquals(1, history.getInt(1));
            }
        } finally {
            dropSchema(url, user, password, schema);
        }
    }

    private static String uniqueSchema(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static void createSchema(String url, String user, String password,
                                     String schema, boolean withLegacyMarker) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            if (withLegacyMarker) {
                statement.execute("CREATE TABLE " + schema
                        + ".legacy_marker (id INTEGER PRIMARY KEY)");
            }
        }
    }

    private static void dropSchema(String url, String user, String password,
                                   String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
