package io.github.bdeeker.sabbathhours.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The same migration and mapping checks against a real PostgreSQL server. Skipped unless
 * {@code SABBATH_TEST_POSTGRES_URL} is set (GitHub Actions sets it with a throwaway database).
 * Point it only at a disposable database: the test deletes every saved location.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SABBATH_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.+")
class PostgresIntegrationTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("SABBATH_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("SABBATH_TEST_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("SABBATH_TEST_POSTGRES_PASSWORD"));
    }

    @Autowired
    private SavedLocationRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void runsOnPostgres() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL");
    }

    @Test
    void roundTripsALocationWithTimestampsInUtc() {
        Instant now = Instant.parse("2026-09-30T16:00:00.123456Z");
        SavedLocation saved = repository.saveAndFlush(new SavedLocation("Sligo", 38.9869, -77.0036, "America/New_York", now));

        SavedLocation loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getCreatedAt()).isEqualTo(now);
        assertThat(loaded.getLongitude()).isEqualTo(-77.0036);
    }

    @Test
    void enforcesTheCheckConstraints() {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 30, 16, 0, 0, 0, ZoneOffset.UTC);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO locations (id, name, latitude, longitude, time_zone, created_at, updated_at, version) "
                        + "VALUES (?, 'x', 95, 0, 'UTC', ?, ?, 0)",
                UUID.randomUUID(), now, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
