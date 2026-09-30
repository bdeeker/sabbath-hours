package io.github.bdeeker.sabbathhours.sabbath;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.bdeeker.sabbathhours.solar.GeoPoint;
import io.github.bdeeker.sabbathhours.solar.SolarCalculator;

/**
 * Sabbath rules on top of the {@link SolarCalculator}: which Friday a date belongs to,
 * a run of upcoming Sabbaths, and whether the Sabbath is in progress at a given moment.
 */
@Service
public class SabbathService {

    public static final int MAX_UPCOMING = 53;

    private final SolarCalculator solarCalculator;

    public SabbathService(SolarCalculator solarCalculator) {
        this.solarCalculator = solarCalculator;
    }

    /**
     * The Sabbath for the week of {@code date}: on a Saturday, the Sabbath that began the evening
     * before; on any other day, the Sabbath that begins on the next Friday (or that same Friday).
     */
    public SabbathWindow forWeekOf(GeoPoint point, ZoneId zone, LocalDate date) {
        LocalDate friday = date.getDayOfWeek() == DayOfWeek.SATURDAY
                ? date.minusDays(1)
                : date.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        return windowFor(point, zone, friday);
    }

    /** {@code count} consecutive Sabbaths, starting with {@link #forWeekOf} {@code from}. */
    public List<SabbathWindow> upcoming(GeoPoint point, ZoneId zone, LocalDate from, int count) {
        if (count < 1 || count > MAX_UPCOMING) {
            throw new IllegalArgumentException("count must be between 1 and " + MAX_UPCOMING + ", was " + count);
        }
        LocalDate friday = forWeekOf(point, zone, from).friday();
        List<SabbathWindow> windows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            windows.add(windowFor(point, zone, friday.plusWeeks(i)));
        }
        return windows;
    }

    /**
     * Whether the Sabbath is in progress at {@code at}.
     *
     * <p>Friday and Saturday are decided by comparing against that week's two sunsets, and are
     * {@code UNDETERMINED} if a needed sunset does not happen. Sunday to Thursday are normally
     * not Sabbath, with one exception: at high latitudes in summer, Saturday's sunset can fall
     * after midnight on Sunday's clock (Fairbanks in June), so a known sunset still ahead wins.
     */
    public SabbathStatus statusAt(GeoPoint point, ZoneId zone, Instant at) {
        LocalDate localDate = at.atZone(zone).toLocalDate();
        LocalDate friday = localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        SabbathWindow window = windowFor(point, zone, friday);
        Optional<Instant> begins = window.beginsAt();
        Optional<Instant> ends = window.endsAt();

        boolean fridayOrSaturday = !localDate.isAfter(window.saturday());
        if (fridayOrSaturday) {
            if (begins.isEmpty()) {
                return new SabbathStatus(SabbathStatus.State.UNDETERMINED, at, window);
            }
            if (at.isBefore(begins.get())) {
                return new SabbathStatus(SabbathStatus.State.NOT_SABBATH, at, window);
            }
            if (ends.isEmpty()) {
                return new SabbathStatus(SabbathStatus.State.UNDETERMINED, at, window);
            }
            if (at.isBefore(ends.get())) {
                return new SabbathStatus(SabbathStatus.State.IN_SABBATH, at, window);
            }
            return new SabbathStatus(SabbathStatus.State.NOT_SABBATH, at, windowFor(point, zone, friday.plusWeeks(1)));
        }

        if (begins.isPresent() && ends.isPresent() && !at.isBefore(begins.get()) && at.isBefore(ends.get())) {
            return new SabbathStatus(SabbathStatus.State.IN_SABBATH, at, window);
        }
        return new SabbathStatus(SabbathStatus.State.NOT_SABBATH, at, windowFor(point, zone, friday.plusWeeks(1)));
    }

    private SabbathWindow windowFor(GeoPoint point, ZoneId zone, LocalDate friday) {
        return new SabbathWindow(
                friday,
                solarCalculator.sunsetEndingDay(point, friday, zone),
                solarCalculator.sunsetEndingDay(point, friday.plusDays(1), zone));
    }
}
