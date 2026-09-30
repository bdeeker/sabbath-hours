package io.github.bdeeker.sabbathhours.location;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;

import io.github.bdeeker.sabbathhours.web.IanaTimeZone;
import io.github.bdeeker.sabbathhours.web.LocationQuery;

/** JSON shapes for the saved-location endpoints. */
public final class LocationDtos {

    private LocationDtos() {
    }

    /** Body of POST and PUT. PUT replaces every field. */
    public record LocationRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotBlank @IanaTimeZone String timeZone) {
    }

    public record LocationResponse(
            UUID id,
            String name,
            double latitude,
            double longitude,
            String timeZone,
            Instant createdAt,
            Instant updatedAt) {

        static LocationResponse of(SavedLocation location) {
            return new LocationResponse(location.getId(), location.getName(), location.getLatitude(),
                    location.getLongitude(), location.getTimeZone(), location.getCreatedAt(), location.getUpdatedAt());
        }
    }

    /** One page of results; {@code page} is zero-based. */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        static PageResponse<LocationResponse> of(Page<SavedLocation> page) {
            return new PageResponse<>(page.map(LocationResponse::of).getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    static LocationQuery toQuery(SavedLocation location) {
        return new LocationQuery(location.getLatitude(), location.getLongitude(), location.getTimeZone());
    }
}
