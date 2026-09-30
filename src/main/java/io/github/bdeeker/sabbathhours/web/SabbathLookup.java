package io.github.bdeeker.sabbathhours.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Component;

import io.github.bdeeker.sabbathhours.sabbath.SabbathService;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.SabbathResult;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.StatusResult;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.UpcomingResult;

/**
 * The three Sabbath lookups, shared by the coordinates endpoints and the saved-location endpoints
 * so both default "today" and "now" the same way: in the location's own time zone.
 */
@Component
public class SabbathLookup {

    private final SabbathService sabbathService;
    private final SabbathResponseMapper mapper;
    private final Clock clock;

    public SabbathLookup(SabbathService sabbathService, SabbathResponseMapper mapper, Clock clock) {
        this.sabbathService = sabbathService;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** @param date null means today at the location */
    public SabbathResult forWeek(LocationQuery location, LocalDate date, Locale locale) {
        return mapper.toResult(location,
                sabbathService.forWeekOf(location.point(), location.zone(), orToday(date, location)), locale);
    }

    /** @param from null means today at the location */
    public UpcomingResult upcoming(LocationQuery location, LocalDate from, int count, Locale locale) {
        return mapper.toUpcoming(location,
                sabbathService.upcoming(location.point(), location.zone(), orToday(from, location), count), locale);
    }

    /** @param at null means now */
    public StatusResult status(LocationQuery location, Instant at, Locale locale) {
        Instant moment = at != null ? at : clock.instant();
        return mapper.toStatus(location, sabbathService.statusAt(location.point(), location.zone(), moment), locale);
    }

    private LocalDate orToday(LocalDate date, LocationQuery location) {
        return date != null ? date : LocalDate.now(clock.withZone(location.zone()));
    }
}
