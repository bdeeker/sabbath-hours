package io.github.bdeeker.sabbathhours.sabbath;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import io.github.bdeeker.sabbathhours.solar.SunsetOutcome;

/**
 * One Sabbath: from the sunset that ends {@code friday} to the sunset that ends the following Saturday.
 * Either edge may be unknown (polar day or night), in which case its outcome is a
 * {@link SunsetOutcome.NoSunset} explaining why.
 */
public record SabbathWindow(LocalDate friday, SunsetOutcome begins, SunsetOutcome ends) {

    public SabbathWindow {
        if (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
            throw new IllegalArgumentException("friday must be a Friday, was " + friday + " (" + friday.getDayOfWeek() + ")");
        }
    }

    public LocalDate saturday() {
        return friday.plusDays(1);
    }

    public Optional<Instant> beginsAt() {
        return instantOf(begins);
    }

    public Optional<Instant> endsAt() {
        return instantOf(ends);
    }

    private static Optional<Instant> instantOf(SunsetOutcome outcome) {
        return outcome instanceof SunsetOutcome.Sunset sunset ? Optional.of(sunset.at()) : Optional.empty();
    }
}
