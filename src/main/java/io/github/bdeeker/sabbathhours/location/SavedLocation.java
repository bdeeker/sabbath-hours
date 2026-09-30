package io.github.bdeeker.sabbathhours.location;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A named place whose Sabbath times can be looked up by ID. */
@Entity
@Table(name = "locations")
public class SavedLocation {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: two concurrent updates can't silently overwrite each other. Null until first saved. */
    @Version
    private Long version;

    protected SavedLocation() {
        // for JPA
    }

    public SavedLocation(String name, double latitude, double longitude, String timeZone, Instant now) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        update(name, latitude, longitude, timeZone, now);
    }

    public void update(String name, double latitude, double longitude, String timeZone, Instant now) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeZone = timeZone;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
