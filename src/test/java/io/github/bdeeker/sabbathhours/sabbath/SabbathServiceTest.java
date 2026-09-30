package io.github.bdeeker.sabbathhours.sabbath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.bdeeker.sabbathhours.sabbath.SabbathStatus.State;
import io.github.bdeeker.sabbathhours.solar.GeoPoint;
import io.github.bdeeker.sabbathhours.solar.SolarCalculator;
import io.github.bdeeker.sabbathhours.solar.SunsetOutcome;

class SabbathServiceTest {

    private static final GeoPoint SILVER_SPRING = new GeoPoint(39.0615, -76.9671);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final GeoPoint FAIRBANKS = new GeoPoint(64.8378, -147.7164);
    private static final ZoneId ANCHORAGE = ZoneId.of("America/Anchorage");
    private static final GeoPoint TROMSO = new GeoPoint(69.6492, 18.9553);
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final SabbathService service = new SabbathService(new SolarCalculator());

    private static Instant local(String dateTime, ZoneId zone) {
        return LocalDateTime.parse(dateTime).atZone(zone).toInstant();
    }

    private static LocalDateTime toMinute(Instant instant, ZoneId zone) {
        return instant.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES).atZone(zone).toLocalDateTime();
    }

    @Nested
    class ForWeekOf {

        @ParameterizedTest(name = "{0} -> Friday {1}")
        @CsvSource({
                "2026-09-30, 2026-10-02", // Wednesday: the coming Friday
                "2026-10-02, 2026-10-02", // Friday: that same Friday
                "2026-10-03, 2026-10-02", // Saturday: the Sabbath that began the evening before
                "2026-10-04, 2026-10-09", // Sunday: next week's Friday
        })
        void picksTheRightFriday(LocalDate date, LocalDate expectedFriday) {
            assertThat(service.forWeekOf(SILVER_SPRING, NEW_YORK, date).friday()).isEqualTo(expectedFriday);
        }

        @Test
        void returnsBothSunsetsForSilverSpring() {
            SabbathWindow window = service.forWeekOf(SILVER_SPRING, NEW_YORK, LocalDate.of(2026, 10, 2));

            // U.S. Naval Observatory: Friday 18:49, Saturday 18:47 EDT.
            assertThat(toMinute(window.beginsAt().orElseThrow(), NEW_YORK)).isEqualTo(LocalDateTime.parse("2026-10-02T18:49"));
            assertThat(toMinute(window.endsAt().orElseThrow(), NEW_YORK)).isEqualTo(LocalDateTime.parse("2026-10-03T18:47"));
        }

        @Test
        void reportsPolarNightInsteadOfATime() {
            SabbathWindow window = service.forWeekOf(TROMSO, OSLO, LocalDate.of(2026, 12, 18));

            assertThat(window.beginsAt()).isEmpty();
            assertThat(window.begins()).isEqualTo(new SunsetOutcome.NoSunset(SunsetOutcome.Reason.POLAR_NIGHT));
            assertThat(window.endsAt()).isEmpty();
        }
    }

    @Nested
    class Upcoming {

        @Test
        void returnsConsecutiveWeeksAcrossTheEndOfDaylightSavingTime() {
            // US daylight saving time ends Sunday, 1 November 2026.
            List<SabbathWindow> windows = service.upcoming(SILVER_SPRING, NEW_YORK, LocalDate.of(2026, 10, 28), 2);

            assertThat(windows).extracting(SabbathWindow::friday)
                    .containsExactly(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 6));
            // U.S. Naval Observatory: 18:09 EDT, then 17:02 EST.
            assertThat(toMinute(windows.get(0).beginsAt().orElseThrow(), NEW_YORK)).isEqualTo(LocalDateTime.parse("2026-10-30T18:09"));
            assertThat(toMinute(windows.get(1).beginsAt().orElseThrow(), NEW_YORK)).isEqualTo(LocalDateTime.parse("2026-11-06T17:02"));
        }

        @Test
        void allowsAFullYearOfSabbaths() {
            assertThat(service.upcoming(SILVER_SPRING, NEW_YORK, LocalDate.of(2026, 1, 1), SabbathService.MAX_UPCOMING))
                    .hasSize(53);
        }

        @Test
        void rejectsCountsOutOfRange() {
            assertThatThrownBy(() -> service.upcoming(SILVER_SPRING, NEW_YORK, LocalDate.of(2026, 10, 1), 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.upcoming(SILVER_SPRING, NEW_YORK, LocalDate.of(2026, 10, 1), 54))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class StatusAt {

        @ParameterizedTest(name = "{0} -> {1}, Sabbath of {2}")
        @CsvSource({
                "2026-09-30T12:00, NOT_SABBATH, 2026-10-02", // Wednesday
                "2026-10-02T18:48, NOT_SABBATH, 2026-10-02", // Friday, a minute before sunset (18:48:48)
                "2026-10-02T18:49, IN_SABBATH,  2026-10-02", // Friday, just after sunset
                "2026-10-03T12:00, IN_SABBATH,  2026-10-02", // Saturday noon
                "2026-10-03T18:46, IN_SABBATH,  2026-10-02", // Saturday, just before sunset
                "2026-10-03T18:48, NOT_SABBATH, 2026-10-09", // Saturday, just after sunset: next week's
                "2026-10-04T00:30, NOT_SABBATH, 2026-10-09", // Sunday
        })
        void silverSpring(String localTime, State expectedState, LocalDate expectedFriday) {
            SabbathStatus status = service.statusAt(SILVER_SPRING, NEW_YORK, local(localTime, NEW_YORK));

            assertThat(status.state()).isEqualTo(expectedState);
            assertThat(status.window().friday()).isEqualTo(expectedFriday);
        }

        @ParameterizedTest(name = "{0} -> {1}, Sabbath of {2}")
        @CsvSource({
                // Friday's sunset is at 00:47 on Saturday's clock, and Saturday's at 00:47 on Sunday's.
                "2026-06-19T23:00, NOT_SABBATH, 2026-06-19", // Friday night, sun still up
                "2026-06-20T00:10, NOT_SABBATH, 2026-06-19", // Saturday's clock, but Friday's sun hasn't set
                "2026-06-20T01:00, IN_SABBATH,  2026-06-19",
                "2026-06-21T00:20, IN_SABBATH,  2026-06-19", // Sunday's clock, Saturday's sun hasn't set
                "2026-06-21T01:30, NOT_SABBATH, 2026-06-26",
        })
        void fairbanksInJuneWhenSunsetsFallAfterMidnight(String localTime, State expectedState, LocalDate expectedFriday) {
            SabbathStatus status = service.statusAt(FAIRBANKS, ANCHORAGE, local(localTime, ANCHORAGE));

            assertThat(status.state()).isEqualTo(expectedState);
            assertThat(status.window().friday()).isEqualTo(expectedFriday);
        }

        @Test
        void isUndeterminedOnFridayAndSaturdayDuringPolarNight() {
            assertThat(service.statusAt(TROMSO, OSLO, local("2026-12-18T20:00", OSLO)).state()).isEqualTo(State.UNDETERMINED);
            assertThat(service.statusAt(TROMSO, OSLO, local("2026-12-19T20:00", OSLO)).state()).isEqualTo(State.UNDETERMINED);
        }

        @Test
        void isNotSabbathMidweekEvenDuringPolarNight() {
            SabbathStatus status = service.statusAt(TROMSO, OSLO, local("2026-12-15T12:00", OSLO));

            assertThat(status.state()).isEqualTo(State.NOT_SABBATH);
            assertThat(status.window().friday()).isEqualTo(LocalDate.of(2026, 12, 18));
        }

        @Test
        void startsExactlyAtSunsetAndEndsExactlyAtSunset() {
            SabbathWindow window = service.forWeekOf(SILVER_SPRING, NEW_YORK, LocalDate.of(2026, 10, 2));
            Instant begins = window.beginsAt().orElseThrow();
            Instant ends = window.endsAt().orElseThrow();

            assertThat(service.statusAt(SILVER_SPRING, NEW_YORK, begins.minusMillis(1)).state()).isEqualTo(State.NOT_SABBATH);
            assertThat(service.statusAt(SILVER_SPRING, NEW_YORK, begins).state()).isEqualTo(State.IN_SABBATH);
            assertThat(service.statusAt(SILVER_SPRING, NEW_YORK, ends.minusMillis(1)).state()).isEqualTo(State.IN_SABBATH);
            assertThat(service.statusAt(SILVER_SPRING, NEW_YORK, ends).state()).isEqualTo(State.NOT_SABBATH);
        }
    }

    /**
     * Checks {@link SabbathService#statusAt} against a simple, independent rule over many random moments:
     * it is Sabbath exactly when the moment falls inside one of the surrounding weeks' windows, and when it
     * isn't, the window returned is the next one that has not begun yet.
     */
    @Test
    void statusAgreesWithTheWindowsAtRandomMoments() {
        record City(GeoPoint point, ZoneId zone) {
        }
        List<City> cities = List.of(
                new City(SILVER_SPRING, NEW_YORK),
                new City(FAIRBANKS, ANCHORAGE),
                new City(new GeoPoint(-33.8688, 151.2093), ZoneId.of("Australia/Sydney")),
                new City(new GeoPoint(31.7683, 35.2137), ZoneId.of("Asia/Jerusalem")),
                new City(new GeoPoint(1.8721, -157.4278), ZoneId.of("Pacific/Kiritimati")),
                new City(new GeoPoint(-54.8019, -68.3030), ZoneId.of("America/Argentina/Ushuaia")),
                new City(new GeoPoint(17.7466, -64.7032), ZoneId.of("America/St_Thomas")),
                new City(new GeoPoint(51.5074, -0.1278), ZoneId.of("Europe/London")));
        Random random = new Random(7);
        Instant start = Instant.parse("2020-01-01T00:00:00Z");
        long span = ChronoUnit.SECONDS.between(start, Instant.parse("2040-01-01T00:00:00Z"));

        for (int i = 0; i < 20_000; i++) {
            City city = cities.get(random.nextInt(cities.size()));
            Instant at = start.plusSeconds((long) (random.nextDouble() * span));
            SabbathStatus status = service.statusAt(city.point(), city.zone(), at);

            LocalDate friday = at.atZone(city.zone()).toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
            boolean inAnyWindow = false;
            for (int week = -1; week <= 1; week++) {
                SabbathWindow w = service.forWeekOf(city.point(), city.zone(), friday.plusWeeks(week));
                if (!at.isBefore(w.beginsAt().orElseThrow()) && at.isBefore(w.endsAt().orElseThrow())) {
                    inAnyWindow = true;
                    assertThat(status.window()).as("window for %s in %s", at, city.zone()).isEqualTo(w);
                }
            }
            assertThat(status.state()).as("%s in %s", at, city.zone())
                    .isEqualTo(inAnyWindow ? State.IN_SABBATH : State.NOT_SABBATH);
            if (!inAnyWindow) {
                assertThat(status.window().beginsAt().orElseThrow()).as("next Sabbath after %s", at).isAfter(at);
                assertThat(Duration.between(at, status.window().beginsAt().orElseThrow())).isLessThan(Duration.ofDays(7));
            }
        }
    }

    @Test
    void windowRejectsANonFriday() {
        SunsetOutcome none = new SunsetOutcome.NoSunset(SunsetOutcome.Reason.POLAR_DAY);
        assertThatThrownBy(() -> new SabbathWindow(LocalDate.of(2026, 10, 3), none, none))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SATURDAY");
    }
}
