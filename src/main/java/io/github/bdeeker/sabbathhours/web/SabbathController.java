package io.github.bdeeker.sabbathhours.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.bdeeker.sabbathhours.sabbath.SabbathService;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.SabbathResult;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.StatusResult;
import io.github.bdeeker.sabbathhours.web.SabbathResponses.UpcomingResult;

/**
 * Sabbath times for any coordinates. Every endpoint takes {@code latitude}, {@code longitude},
 * and {@code timeZone} (an IANA ID) as query parameters; summaries follow {@code Accept-Language}.
 */
@RestController
@RequestMapping("/api/v1/sabbath")
public class SabbathController {

    private final SabbathService sabbathService;
    private final SabbathResponseMapper mapper;
    private final Clock clock;

    public SabbathController(SabbathService sabbathService, SabbathResponseMapper mapper, Clock clock) {
        this.sabbathService = sabbathService;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** The Sabbath for the week of {@code date} (default: today at the location). */
    @GetMapping
    public SabbathResult forWeek(
            @Valid LocationQuery location,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Locale locale) {
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(location.zone()));
        return mapper.toResult(location, sabbathService.forWeekOf(location.point(), location.zone(), day), locale);
    }

    /** {@code count} consecutive Sabbaths starting with the week of {@code from} (default: today). */
    @GetMapping("/upcoming")
    public UpcomingResult upcoming(
            @Valid LocationQuery location,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "4") @Min(1) @Max(SabbathService.MAX_UPCOMING) int count,
            Locale locale) {
        LocalDate day = from != null ? from : LocalDate.now(clock.withZone(location.zone()));
        return mapper.toUpcoming(location, sabbathService.upcoming(location.point(), location.zone(), day, count), locale);
    }

    /** Whether the Sabbath is in progress at {@code at} (an ISO-8601 instant; default: now). */
    @GetMapping("/status")
    public StatusResult status(
            @Valid LocationQuery location,
            @RequestParam(required = false) Instant at,
            Locale locale) {
        Instant moment = at != null ? at : clock.instant();
        return mapper.toStatus(location, sabbathService.statusAt(location.point(), location.zone(), moment), locale);
    }
}
