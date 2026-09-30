package io.github.bdeeker.sabbathhours.solar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SolarCalculatorTest {

    private final SolarCalculator calculator = new SolarCalculator();

    /**
     * Reference sunsets from the U.S. Naval Observatory "Rise/Set/Transit" API
     * (aa.usno.navy.mil/api/rstt/oneday), which publishes times rounded to the minute.
     * Columns: zone, latitude, longitude, civil date, expected local sunset date-time.
     */
    @ParameterizedTest(name = "{0} {3} -> {4}")
    @CsvSource({
            // Silver Spring, MD (GC headquarters): EDT and EST
            "America/New_York,              39.0615,  -76.9671, 2026-10-02, 2026-10-02T18:49",
            "America/New_York,              39.0615,  -76.9671, 2026-10-03, 2026-10-03T18:47",
            "America/New_York,              39.0615,  -76.9671, 2026-12-18, 2026-12-18T16:47",
            // Christiansted, St. Croix
            "America/St_Thomas,             17.7466,  -64.7032, 2026-10-02, 2026-10-02T18:07",
            // Southern hemisphere, daylight saving time
            "Australia/Sydney,             -33.8688,  151.2093, 2026-03-20, 2026-03-20T19:07",
            "America/Argentina/Ushuaia,    -54.8019,  -68.3030, 2026-01-02, 2026-01-02T22:12",
            // Near the equator
            "Africa/Nairobi,                -1.2921,   36.8219, 2026-10-02, 2026-10-02T18:26",
            // Prime meridian, British Summer Time
            "Europe/London,                 51.5074,   -0.1278, 2026-10-02, 2026-10-02T18:36",
            // Jerusalem: daylight saving time starts on this Friday at 02:00
            "Asia/Jerusalem,                31.7683,   35.2137, 2026-03-27, 2026-03-27T18:55",
            "Asia/Jerusalem,                31.7683,   35.2137, 2026-03-28, 2026-03-28T18:56",
            // Kiritimati: UTC+14 at 157 W, the civil date runs a day ahead of the sun
            "Pacific/Kiritimati,             1.8721, -157.4278, 2026-10-02, 2026-10-02T18:22",
            "Pacific/Kiritimati,             1.8721, -157.4278, 2026-10-03, 2026-10-03T18:22",
            // Fairbanks in June: the day's sunset happens after midnight, on the next clock date
            "America/Anchorage,             64.8378, -147.7164, 2026-06-19, 2026-06-20T00:47",
    })
    void matchesNavalObservatoryToTheMinute(String zone, double lat, double lon, LocalDate date, LocalDateTime expectedLocal) {
        ZoneId zoneId = ZoneId.of(zone);
        SunsetOutcome outcome = calculator.sunsetEndingDay(new GeoPoint(lat, lon), date, zoneId);

        assertThat(outcome).isInstanceOf(SunsetOutcome.Sunset.class);
        Instant actual = ((SunsetOutcome.Sunset) outcome).at();
        // USNO publishes times rounded to the nearest minute, so compare after the same rounding.
        Instant roundedToMinute = actual.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES);
        assertThat(roundedToMinute.atZone(zoneId).toLocalDateTime()).isEqualTo(expectedLocal);
    }

    @Test
    void reportsPolarNightInTromsoInDecember() {
        SunsetOutcome outcome = calculator.sunsetEndingDay(
                new GeoPoint(69.6492, 18.9553), LocalDate.of(2026, 12, 18), ZoneId.of("Europe/Oslo"));

        assertThat(outcome).isEqualTo(new SunsetOutcome.NoSunset(SunsetOutcome.Reason.POLAR_NIGHT));
    }

    @Test
    void reportsPolarDayInTromsoInJune() {
        SunsetOutcome outcome = calculator.sunsetEndingDay(
                new GeoPoint(69.6492, 18.9553), LocalDate.of(2026, 6, 19), ZoneId.of("Europe/Oslo"));

        assertThat(outcome).isEqualTo(new SunsetOutcome.NoSunset(SunsetOutcome.Reason.POLAR_DAY));
    }

    @Test
    void handlesTheExactPoles() {
        LocalDate juneSolstice = LocalDate.of(2026, 6, 21);
        ZoneId utc = ZoneId.of("UTC");

        assertThat(calculator.sunsetEndingDay(new GeoPoint(90.0, 0.0), juneSolstice, utc))
                .isEqualTo(new SunsetOutcome.NoSunset(SunsetOutcome.Reason.POLAR_DAY));
        assertThat(calculator.sunsetEndingDay(new GeoPoint(-90.0, 0.0), juneSolstice, utc))
                .isEqualTo(new SunsetOutcome.NoSunset(SunsetOutcome.Reason.POLAR_NIGHT));
    }

    @Test
    void reportsADateThatDoesNotExistInTheZone() {
        // Samoa moved across the date line and skipped Friday, 30 December 2011 entirely.
        SunsetOutcome outcome = calculator.sunsetEndingDay(
                new GeoPoint(-13.8333, -171.7667), LocalDate.of(2011, 12, 30), ZoneId.of("Pacific/Apia"));

        assertThat(outcome).isEqualTo(new SunsetOutcome.NoSunset(SunsetOutcome.Reason.DATE_NOT_IN_ZONE));
    }

    @Test
    void worksAtTheDateLine() {
        for (double lon : new double[] {-180.0, 180.0}) {
            SunsetOutcome outcome = calculator.sunsetEndingDay(
                    new GeoPoint(-17.0, lon), LocalDate.of(2026, 10, 2), ZoneId.of("Pacific/Fiji"));
            assertThat(outcome).isInstanceOf(SunsetOutcome.Sunset.class);
            LocalDateTime local = LocalDateTime.ofInstant(((SunsetOutcome.Sunset) outcome).at(), ZoneId.of("Pacific/Fiji"));
            // Fiji is UTC+12 and 180 degrees is 12 hours from Greenwich, so sunset lands on the same evening.
            assertThat(local.toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 2));
            assertThat(local.getHour()).isBetween(17, 19);
        }
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> new GeoPoint(90.01, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, -180.01)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
