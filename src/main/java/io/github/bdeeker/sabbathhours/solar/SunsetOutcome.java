package io.github.bdeeker.sabbathhours.solar;

import java.time.Instant;

/**
 * The result of asking for the sunset that ends one local day's daylight.
 * Either the sun sets at a specific instant, or it does not set at all that day.
 */
public sealed interface SunsetOutcome {

    /** The sun sets at {@code at}. */
    record Sunset(Instant at) implements SunsetOutcome {
    }

    /** The sun does not set on this day, for the given reason. */
    record NoSunset(Reason reason) implements SunsetOutcome {
    }

    enum Reason {
        /** The sun stays above the horizon all day (midnight sun). */
        POLAR_DAY,
        /** The sun stays below the horizon all day (polar night). */
        POLAR_NIGHT,
        /** The requested calendar date does not exist in that time zone (e.g. Samoa skipped 2011-12-30). */
        DATE_NOT_IN_ZONE
    }
}
