package io.github.bdeeker.sabbathhours.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import io.github.bdeeker.sabbathhours.config.ValidationConfig;
import io.github.bdeeker.sabbathhours.config.WebConfig;
import io.github.bdeeker.sabbathhours.sabbath.SabbathService;
import io.github.bdeeker.sabbathhours.solar.SolarCalculator;

@WebMvcTest(SabbathController.class)
@Import({SabbathService.class, SolarCalculator.class, SabbathResponseMapper.class, WebConfig.class,
        ValidationConfig.class, MovableClockConfig.class})
class SabbathControllerTest {

    /** Wednesday, 30 September 2026, noon in Silver Spring. */
    static final Instant NOW = Instant.parse("2026-09-30T16:00:00Z");

    private static final String SILVER_SPRING = "latitude=39.0615&longitude=-76.9671&timeZone=America/New_York";
    private static final String TROMSO = "latitude=69.6492&longitude=18.9553&timeZone=Europe/Oslo";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MovableClockConfig.MovableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(NOW);
    }

    private MvcTestResult get(String url) {
        return mvc.get().uri(url).exchange();
    }

    @Nested
    class ThisWeek {

        @Test
        void returnsSunsetsInLocalTimeRoundedToTheMinute() {
            MvcTestResult result = get("/api/v1/sabbath?" + SILVER_SPRING + "&date=2026-10-02");

            assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
            assertThat(result).bodyJson().extractingPath("$.location.timeZone").isEqualTo("America/New_York");
            assertThat(result).bodyJson().extractingPath("$.sabbath.friday").isEqualTo("2026-10-02");
            assertThat(result).bodyJson().extractingPath("$.sabbath.saturday").isEqualTo("2026-10-03");
            assertThat(result).bodyJson().extractingPath("$.sabbath.begins.type").isEqualTo("SUNSET");
            assertThat(result).bodyJson().extractingPath("$.sabbath.begins.time").isEqualTo("2026-10-02T18:49:00-04:00");
            assertThat(result).bodyJson().extractingPath("$.sabbath.ends.time").isEqualTo("2026-10-03T18:47:00-04:00");
            assertThat(result).bodyJson().extractingPath("$.sabbath.summary")
                    .isEqualTo("Sabbath begins Friday, October 2 at 6:49 PM and ends Saturday, October 3 at 6:47 PM.");
        }

        @Test
        void defaultsToTodayAtTheLocation() {
            assertThat(get("/api/v1/sabbath?" + SILVER_SPRING)).bodyJson()
                    .extractingPath("$.sabbath.friday").isEqualTo("2026-10-02");
        }

        @Test
        void usesTheLocationsOwnDateForToday() {
            // 01:00 UTC on 1 November: Saturday 31 October, 21:00 in New York, but Sunday 1 November, 12:00 in Sydney.
            clock.set(Instant.parse("2026-11-01T01:00:00Z"));

            assertThat(get("/api/v1/sabbath?" + SILVER_SPRING))
                    .bodyJson().extractingPath("$.sabbath.friday").isEqualTo("2026-10-30");
            assertThat(get("/api/v1/sabbath?latitude=-33.8688&longitude=151.2093&timeZone=Australia/Sydney"))
                    .bodyJson().extractingPath("$.sabbath.friday").isEqualTo("2026-11-06");
        }

        @Test
        void reportsPolarNightWithNoTime() {
            MvcTestResult result = get("/api/v1/sabbath?" + TROMSO + "&date=2026-12-18");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.sabbath.begins.type").isEqualTo("POLAR_NIGHT");
            assertThat(result).bodyJson().extractingPath("$.sabbath.begins.time").isNull();
            assertThat(result).bodyJson().extractingPath("$.sabbath.summary").asString()
                    .startsWith("Sabbath times cannot be calculated");
        }
    }

    @Nested
    class Languages {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "es    | El sábado comienza el viernes 2 de octubre a las 18:49 y termina el sábado 3 de octubre a las 18:47.",
                "es-MX | El sábado comienza el viernes 2 de octubre a las 18:49 y termina el sábado 3 de octubre a las 18:47.",
                "fr    | Le sabbat commence le vendredi 2 octobre à 18:49 et se termine le samedi 3 octobre à 18:47.",
                "pt-BR | Início do sábado: sexta-feira, 2 de outubro, às 18:49. Término: sábado, 3 de outubro, às 18:47.",
                "de    | Sabbath begins Friday, October 2 at 6:49 PM and ends Saturday, October 3 at 6:47 PM.",
        })
        void summaryFollowsAcceptLanguage(String language, String expected) {
            MvcTestResult result = mvc.get().uri("/api/v1/sabbath?" + SILVER_SPRING + "&date=2026-10-02")
                    .header("Accept-Language", language).exchange();

            assertThat(result).bodyJson().extractingPath("$.sabbath.summary").isEqualTo(expected);
        }
    }

    @Nested
    class Upcoming {

        @Test
        void returnsTheRequestedNumberOfWeeks() {
            MvcTestResult result = get("/api/v1/sabbath/upcoming?" + SILVER_SPRING + "&from=2026-10-01&count=3");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.sabbaths[*].friday")
                    .asArray().containsExactly("2026-10-02", "2026-10-09", "2026-10-16");
        }

        @Test
        void defaultsToFourWeeksFromToday() {
            assertThat(get("/api/v1/sabbath/upcoming?" + SILVER_SPRING)).bodyJson()
                    .extractingPath("$.sabbaths[*].friday").asArray()
                    .containsExactly("2026-10-02", "2026-10-09", "2026-10-16", "2026-10-23");
        }

        @ParameterizedTest
        @CsvSource({"0", "54", "-1"})
        void rejectsCountOutOfRange(String count) {
            MvcTestResult result = get("/api/v1/sabbath/upcoming?" + SILVER_SPRING + "&count=" + count);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("count");
        }

        @Test
        void rejectsANonNumericCount() {
            MvcTestResult result = get("/api/v1/sabbath/upcoming?" + SILVER_SPRING + "&count=abc");

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("must be a whole number");
        }
    }

    @Nested
    class Status {

        @Test
        void isInSabbathOnSaturdayMorning() {
            MvcTestResult result = get("/api/v1/sabbath/status?" + SILVER_SPRING + "&at=2026-10-03T14:00:00Z");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.state").isEqualTo("IN_SABBATH");
            assertThat(result).bodyJson().extractingPath("$.at").isEqualTo("2026-10-03T14:00:00Z");
            assertThat(result).bodyJson().extractingPath("$.sabbath.friday").isEqualTo("2026-10-02");
        }

        @Test
        void acceptsAnInstantWithAnOffset() {
            assertThat(get("/api/v1/sabbath/status?" + SILVER_SPRING + "&at=2026-10-02T18:50:00-04:00"))
                    .bodyJson().extractingPath("$.state").isEqualTo("IN_SABBATH");
        }

        @Test
        void defaultsToNowAndReturnsTheNextSabbath() {
            MvcTestResult result = get("/api/v1/sabbath/status?" + SILVER_SPRING);

            assertThat(result).bodyJson().extractingPath("$.state").isEqualTo("NOT_SABBATH");
            assertThat(result).bodyJson().extractingPath("$.at").isEqualTo("2026-09-30T16:00:00Z");
            assertThat(result).bodyJson().extractingPath("$.sabbath.friday").isEqualTo("2026-10-02");
        }

        @Test
        void rejectsAnUnreadableInstant() {
            MvcTestResult result = get("/api/v1/sabbath/status?" + SILVER_SPRING + "&at=tomorrow");

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("at");
        }
    }

    @Nested
    class Validation {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "longitude=-76.9671&timeZone=America/New_York             | latitude  | is required",
                "latitude=39&timeZone=America/New_York                    | longitude | is required",
                "latitude=39&longitude=-76.9671                           | timeZone  | is required",
                "latitude=39&longitude=-76.9671&timeZone=                 | timeZone  | is required",
                "latitude=91&longitude=-76.9671&timeZone=America/New_York | latitude  | must be less than or equal to 90.0",
                "latitude=39&longitude=-181&timeZone=America/New_York     | longitude | must be greater than or equal to -180.0",
                "latitude=abc&longitude=-76.9671&timeZone=America/New_York | latitude | has an invalid format",
        })
        void rejectsBadCoordinatesAndMissingFields(String query, String field, String message) {
            MvcTestResult result = get("/api/v1/sabbath?" + query);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid request");
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
            assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo(message);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"-04:00", "+05:30", "Z", "UTC+5", "GMT-03:00", "America/Nowhere", "america/new_york"})
        void rejectsUtcOffsetsAndUnknownZones(String zone) {
            MvcTestResult result = mvc.get().uri("/api/v1/sabbath")
                    .param("latitude", "39").param("longitude", "-77").param("timeZone", zone).exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("timeZone");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"UTC", "Etc/UTC", "Asia/Kolkata", "Pacific/Kiritimati"})
        void acceptsRealZoneIds(String zone) {
            assertThat(mvc.get().uri("/api/v1/sabbath")
                    .param("latitude", "39").param("longitude", "-77").param("timeZone", zone).exchange())
                    .hasStatusOk();
        }

        @Test
        void keepsErrorMessagesInEnglishWhateverTheAcceptLanguage() {
            // Error messages are for developers and stay in one language; only the summary is translated.
            MvcTestResult result = mvc.get().uri("/api/v1/sabbath?latitude=91&timeZone=America/New_York")
                    .header("Accept-Language", "es").exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.errors[*].message").asArray()
                    .containsExactly("must be less than or equal to 90.0", "is required");
        }

        @Test
        void rejectsNotANumberLatitude() {
            assertThat(get("/api/v1/sabbath?latitude=NaN&longitude=-76.9671&timeZone=America/New_York"))
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void rejectsAnImpossibleDate() {
            MvcTestResult result = get("/api/v1/sabbath?" + SILVER_SPRING + "&date=2026-13-01");

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("date");
            assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("must be a date in the form YYYY-MM-DD");
        }

        @Test
        void returns404ProblemForUnknownPaths() {
            assertThat(get("/api/v1/nope")).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        }
    }
}
