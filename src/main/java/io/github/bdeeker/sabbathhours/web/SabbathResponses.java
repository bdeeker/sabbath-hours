package io.github.bdeeker.sabbathhours.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** JSON shapes returned by the Sabbath endpoints. */
public final class SabbathResponses {

    private SabbathResponses() {
    }

    public record Location(double latitude, double longitude, String timeZone) {
    }

    /**
     * One edge of the Sabbath.
     *
     * @param type {@code SUNSET} when {@code time} is set; otherwise why there is no time:
     *             {@code POLAR_DAY}, {@code POLAR_NIGHT}, or {@code DATE_NOT_IN_ZONE}
     * @param time the sunset in the location's local time, rounded to the minute, or null
     */
    public record SunsetEvent(String type, OffsetDateTime time) {
    }

    public record Sabbath(
            LocalDate friday,
            LocalDate saturday,
            SunsetEvent begins,
            SunsetEvent ends,
            String summary) {
    }

    public record SabbathResult(Location location, Sabbath sabbath) {
    }

    public record UpcomingResult(Location location, List<Sabbath> sabbaths) {
    }

    /**
     * @param sabbath the Sabbath in progress when {@code state} is {@code IN_SABBATH},
     *                otherwise the next one (or the one that could not be determined)
     */
    public record StatusResult(Location location, String state, Instant at, Sabbath sabbath) {
    }
}
