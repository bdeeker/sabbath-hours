package io.github.bdeeker.sabbathhours.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the real Flyway migration (then Hibernate's schema validation) and checks that the
 * database itself rejects bad rows, even if something bypasses the API's validation.
 */
@DataJpaTest
class LocationPersistenceTest {

    @Autowired
    private SavedLocationRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsALocation() {
        Instant now = Instant.parse("2026-09-30T16:00:00Z");
        SavedLocation saved = repository.saveAndFlush(new SavedLocation("Sligo", 38.9869, -77.0036, "America/New_York", now));

        SavedLocation loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Sligo");
        assertThat(loaded.getLatitude()).isEqualTo(38.9869);
        assertThat(loaded.getCreatedAt()).isEqualTo(now);
        assertThat(loaded.getVersion()).isZero();
    }

    @ParameterizedTest(name = "name=''{0}'' lat={1} lon={2}")
    @CsvSource({"'   ', 1, 1", "ok, 90.5, 1", "ok, 1, -180.5"})
    void databaseRejectsRowsTheApiWouldReject(String name, double latitude, double longitude) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 30, 16, 0, 0, 0, ZoneOffset.UTC);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO locations (id, name, latitude, longitude, time_zone, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, ?, 'UTC', ?, ?, 0)",
                UUID.randomUUID(), name, latitude, longitude, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
