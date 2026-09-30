package io.github.bdeeker.sabbathhours.location;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Create, read, update, and delete saved locations. Input is validated at the API edge before it gets here. */
@Service
@Transactional(readOnly = true)
public class LocationService {

    /** Name first for people, then ID so rows with the same name page in a stable order. */
    private static final Sort LIST_ORDER = Sort.by("name").and(Sort.by("id"));

    private final SavedLocationRepository repository;
    private final Clock clock;

    public LocationService(SavedLocationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public SavedLocation get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new LocationNotFoundException(id));
    }

    public Page<SavedLocation> list(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, LIST_ORDER));
    }

    @Transactional
    public SavedLocation create(String name, double latitude, double longitude, String timeZone) {
        return repository.save(new SavedLocation(name.strip(), latitude, longitude, timeZone, now()));
    }

    @Transactional
    public SavedLocation update(UUID id, String name, double latitude, double longitude, String timeZone) {
        SavedLocation location = get(id);
        location.update(name.strip(), latitude, longitude, timeZone, now());
        // Flush now so an optimistic-lock conflict surfaces here, inside the request, as a 409.
        return repository.saveAndFlush(location);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(get(id));
    }

    /**
     * Milliseconds, not the clock's full nanoseconds: the database keeps microseconds at most, so a finer value
     * would make the POST response disagree with every later GET of the same location.
     */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
