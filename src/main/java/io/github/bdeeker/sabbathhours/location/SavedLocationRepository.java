package io.github.bdeeker.sabbathhours.location;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedLocationRepository extends JpaRepository<SavedLocation, UUID> {
}
