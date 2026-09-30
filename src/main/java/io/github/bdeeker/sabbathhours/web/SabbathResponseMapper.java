package io.github.bdeeker.sabbathhours.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import io.github.bdeeker.sabbathhours.sabbath.SabbathStatus;
import io.github.bdeeker.sabbathhours.sabbath.SabbathWindow;
import io.github.bdeeker.sabbathhours.solar.SunsetOutcome;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.Location;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.Sabbath;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.SabbathResult;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.StatusResult;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.SunsetEvent;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.UpcomingResult;

/** Turns domain results into JSON responses, including the human-readable summary in the request's language. */
@Component
public class SabbathResponseMapper {

    private final MessageSource messages;

    public SabbathResponseMapper(MessageSource messages) {
        this.messages = messages;
    }

    public SabbathResult toResult(LocationQuery query, SabbathWindow window, Locale locale) {
        return new SabbathResult(location(query), sabbath(window, query.zone(), locale));
    }

    public UpcomingResult toUpcoming(LocationQuery query, List<SabbathWindow> windows, Locale locale) {
        ZoneId zone = query.zone();
        return new UpcomingResult(location(query), windows.stream().map(w -> sabbath(w, zone, locale)).toList());
    }

    public StatusResult toStatus(LocationQuery query, SabbathStatus status, Locale locale) {
        return new StatusResult(location(query), status.state().name(), status.at(),
                sabbath(status.window(), query.zone(), locale));
    }

    private static Location location(LocationQuery query) {
        return new Location(query.latitude(), query.longitude(), query.timeZone());
    }

    private Sabbath sabbath(SabbathWindow window, ZoneId zone, Locale locale) {
        return new Sabbath(
                window.friday(),
                window.saturday(),
                event(window.begins(), zone),
                event(window.ends(), zone),
                summary(window, zone, locale));
    }

    private static SunsetEvent event(SunsetOutcome outcome, ZoneId zone) {
        return switch (outcome) {
            case SunsetOutcome.Sunset(Instant at) -> new SunsetEvent("SUNSET", at.atZone(zone).toOffsetDateTime());
            case SunsetOutcome.NoSunset(SunsetOutcome.Reason reason) -> new SunsetEvent(reason.name(), null);
        };
    }

    private String summary(SabbathWindow window, ZoneId zone, Locale locale) {
        if (window.beginsAt().isEmpty() || window.endsAt().isEmpty()) {
            return messages.getMessage("sabbath.summary.undetermined", null, locale);
        }
        DateTimeFormatter day = DateTimeFormatter.ofPattern(messages.getMessage("format.day", null, locale), locale);
        DateTimeFormatter time = DateTimeFormatter.ofPattern(messages.getMessage("format.time", null, locale), locale);
        var begins = window.beginsAt().get().atZone(zone);
        var ends = window.endsAt().get().atZone(zone);
        return messages.getMessage("sabbath.summary", new Object[] {
                day.format(begins), time.format(begins), day.format(ends), time.format(ends)}, locale);
    }
}
