package io.github.bdeeker.sabbathhours.location;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.bdeeker.sabbathhours.location.LocationDtos.LocationRequest;
import io.github.bdeeker.sabbathhours.location.LocationDtos.LocationResponse;
import io.github.bdeeker.sabbathhours.location.LocationDtos.PageResponse;
import io.github.bdeeker.sabbathhours.sabbath.SabbathService;
import io.github.bdeeker.sabbathhours.web.SabbathLookup;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.SabbathResult;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.StatusResult;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.UpcomingResult;

/**
 * Saved locations. Reading is public; creating, replacing, and deleting require the write API key
 * (see {@code security/SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    public static final int MAX_PAGE_SIZE = 100;

    private final LocationService locations;
    private final SabbathLookup lookup;

    public LocationController(LocationService locations, SabbathLookup lookup) {
        this.locations = locations;
        this.lookup = lookup;
    }

    @GetMapping
    public PageResponse<LocationResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return PageResponse.of(locations.list(page, size));
    }

    @GetMapping("/{id}")
    public LocationResponse get(@PathVariable UUID id) {
        return LocationResponse.of(locations.get(id));
    }

    @PostMapping
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
        SavedLocation saved = locations.create(request.name(), request.latitude(), request.longitude(), request.timeZone());
        // Relative on purpose: an absolute URL would be built from the request's Host header, which the caller controls.
        return ResponseEntity.created(URI.create("/api/v1/locations/" + saved.getId())).body(LocationResponse.of(saved));
    }

    @PutMapping("/{id}")
    public LocationResponse replace(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return LocationResponse.of(
                locations.update(id, request.name(), request.latitude(), request.longitude(), request.timeZone()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        locations.delete(id);
    }

    @GetMapping("/{id}/sabbath")
    public SabbathResult sabbath(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Locale locale) {
        return lookup.forWeek(LocationDtos.toQuery(locations.get(id)), date, locale);
    }

    @GetMapping("/{id}/sabbath/upcoming")
    public UpcomingResult upcoming(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "4") @Min(1) @Max(SabbathService.MAX_UPCOMING) int count,
            Locale locale) {
        return lookup.upcoming(LocationDtos.toQuery(locations.get(id)), from, count, locale);
    }

    @GetMapping("/{id}/sabbath/status")
    public StatusResult status(
            @PathVariable UUID id,
            @RequestParam(required = false) Instant at,
            Locale locale) {
        return lookup.status(LocationDtos.toQuery(locations.get(id)), at, locale);
    }
}
