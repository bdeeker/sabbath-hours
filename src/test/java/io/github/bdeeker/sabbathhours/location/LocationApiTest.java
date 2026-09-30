package io.github.bdeeker.sabbathhours.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.bdeeker.sabbathhours.web.MovableClockConfig;

@SpringBootTest(properties = "sabbath.api.write-key=" + LocationApiTest.KEY)
@AutoConfigureMockMvc
@Import(MovableClockConfig.class)
class LocationApiTest {

    static final String KEY = "test-key-0123456789-abcdefghijklmnop";
    static final Instant T0 = Instant.parse("2026-09-30T16:00:00Z");

    private static final String SILVER_SPRING_JSON =
            "{\"name\":\"  Sligo SDA Church  \",\"latitude\":38.9869,\"longitude\":-77.0036,\"timeZone\":\"America/New_York\"}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private SavedLocationRepository repository;

    @Autowired
    private MovableClockConfig.MovableClock clock;

    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        clock.set(T0);
    }

    private MvcTestResult post(String json, String key) {
        var request = mvc.post().uri("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("X-API-Key", key);
        }
        return request.exchange();
    }

    private UUID createSilverSpring() {
        MvcTestResult result = post(SILVER_SPRING_JSON, KEY);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(result.getResponse().getHeader("Location").substring("/api/v1/locations/".length()));
    }

    @Nested
    class Create {

        @Test
        void createsATrimmedLocationWithARelativeLocationHeader() {
            MvcTestResult result = post(SILVER_SPRING_JSON, KEY);

            assertThat(result).hasStatus(HttpStatus.CREATED);
            String location = result.getResponse().getHeader("Location");
            assertThat(location).matches("/api/v1/locations/[0-9a-f-]{36}");
            assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Sligo SDA Church");
            assertThat(result).bodyJson().extractingPath("$.timeZone").isEqualTo("America/New_York");
            assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-09-30T16:00:00Z");
            assertThat(result).bodyJson().extractingPath("$.updatedAt").isEqualTo("2026-09-30T16:00:00Z");
            assertThat(repository.count()).isEqualTo(1);
        }

        @ParameterizedTest(name = "{1}")
        @CsvSource(delimiter = '|', value = {
                "{\"name\":\" \",\"latitude\":1,\"longitude\":1,\"timeZone\":\"UTC\"}                  | name",
                "{\"latitude\":1,\"longitude\":1,\"timeZone\":\"UTC\"}                                | name",
                "{\"name\":\"x\",\"latitude\":91,\"longitude\":1,\"timeZone\":\"UTC\"}                | latitude",
                "{\"name\":\"x\",\"latitude\":1,\"timeZone\":\"UTC\"}                                 | longitude",
                "{\"name\":\"x\",\"latitude\":1,\"longitude\":1,\"timeZone\":\"-04:00\"}              | timeZone",
        })
        void rejectsInvalidBodies(String json, String field) {
            MvcTestResult result = post(json, KEY);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
            assertThat(repository.count()).isZero();
        }

        @Test
        void createResponseAndLaterReadsAgreeOnTimestamps() {
            clock.set(T0.plusNanos(123_456_789));

            MvcTestResult created = post(SILVER_SPRING_JSON, KEY);
            String id = created.getResponse().getHeader("Location").substring("/api/v1/locations/".length());

            assertThat(created).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-09-30T16:00:00.123Z");
            assertThat(mvc.get().uri("/api/v1/locations/" + id).exchange())
                    .bodyJson().extractingPath("$.createdAt").isEqualTo("2026-09-30T16:00:00.123Z");
        }

        @Test
        void rejectsANameOfOnlyUnicodeSpaces() {
            // Regression guard: @NotBlank and the service's strip() must agree on what "blank" means (both use
            // Character.isWhitespace), or an em space (U+2003) would pass validation and then hit the database check.
            MvcTestResult result = post("{\"name\":\"\\u2003\\u2003\",\"latitude\":1,\"longitude\":1,\"timeZone\":\"UTC\"}", KEY);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
            assertThat(repository.count()).isZero();
        }

        @Test
        void rejectsANameLongerThan200Characters() {
            String json = "{\"name\":\"" + "a".repeat(201) + "\",\"latitude\":1,\"longitude\":1,\"timeZone\":\"UTC\"}";

            assertThat(post(json, KEY)).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void rejectsMalformedJson() {
            assertThat(post("{\"name\":", KEY)).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        }
    }

    @Nested
    class WriteSecurity {

        @Test
        void refusesACreateWithNoKey() {
            MvcTestResult result = post(SILVER_SPRING_JSON, null);

            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Unauthorized");
            assertThat(repository.count()).isZero();
        }

        @ParameterizedTest
        @CsvSource({"wrong-key-0123456789-abcdefghijklmn", "test-key-0123456789-abcdefghijklmnoX", "test-key"})
        void refusesACreateWithTheWrongKey(String key) {
            assertThat(post(SILVER_SPRING_JSON, key)).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(repository.count()).isZero();
        }

        @Test
        void refusesReplaceAndDeleteWithNoKey() {
            UUID id = createSilverSpring();

            assertThat(mvc.put().uri("/api/v1/locations/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content(SILVER_SPRING_JSON).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(mvc.delete().uri("/api/v1/locations/" + id).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        void refusesWritesToPathsWithNoWriteRule() {
            assertThat(mvc.post().uri("/api/v1/sabbath").header("X-API-Key", KEY).exchange())
                    .hasStatus(HttpStatus.FORBIDDEN);
            assertThat(mvc.post().uri("/anything").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void allowsBrowserReadsFromAnyOrigin() {
            MvcTestResult preflight = mvc.options().uri("/api/v1/sabbath")
                    .header("Origin", "https://church.example.org")
                    .header("Access-Control-Request-Method", "GET").exchange();

            assertThat(preflight).hasStatusOk();
            assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("*");
        }

        @Test
        void refusesBrowserWritesFromOtherOrigins() {
            MvcTestResult preflight = mvc.options().uri("/api/v1/locations")
                    .header("Origin", "https://church.example.org")
                    .header("Access-Control-Request-Method", "POST").exchange();

            assertThat(preflight).hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        void sendsStandardSecurityHeaders() {
            MvcTestResult result = mvc.get().uri("/api/v1/locations").exchange();

            assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        }
    }

    @Nested
    class ReadUpdateDelete {

        @Test
        void readsALocationWithoutAKey() {
            UUID id = createSilverSpring();

            MvcTestResult result = mvc.get().uri("/api/v1/locations/" + id).exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(id.toString());
        }

        @Test
        void returns404ForAnUnknownId() {
            MvcTestResult result = mvc.get().uri("/api/v1/locations/" + UUID.randomUUID()).exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        }

        @Test
        void returns400ForAMalformedId() {
            MvcTestResult result = mvc.get().uri("/api/v1/locations/not-a-uuid").exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("must be a UUID");
        }

        @Test
        void replacesEveryFieldAndKeepsCreatedAt() {
            UUID id = createSilverSpring();
            clock.set(T0.plusSeconds(3600));

            MvcTestResult result = mvc.put().uri("/api/v1/locations/" + id).header("X-API-Key", KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Christiansted SDA Temple\",\"latitude\":17.7466,\"longitude\":-64.7032,\"timeZone\":\"America/St_Thomas\"}")
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Christiansted SDA Temple");
            assertThat(result).bodyJson().extractingPath("$.timeZone").isEqualTo("America/St_Thomas");
            assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-09-30T16:00:00Z");
            assertThat(result).bodyJson().extractingPath("$.updatedAt").isEqualTo("2026-09-30T17:00:00Z");
        }

        @Test
        void replaceOfAnUnknownIdIs404() {
            assertThat(mvc.put().uri("/api/v1/locations/" + UUID.randomUUID()).header("X-API-Key", KEY)
                    .contentType(MediaType.APPLICATION_JSON).content(SILVER_SPRING_JSON).exchange())
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void deletesAndThenReports404() {
            UUID id = createSilverSpring();

            assertThat(mvc.delete().uri("/api/v1/locations/" + id).header("X-API-Key", KEY).exchange())
                    .hasStatus(HttpStatus.NO_CONTENT);
            assertThat(mvc.get().uri("/api/v1/locations/" + id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(mvc.delete().uri("/api/v1/locations/" + id).header("X-API-Key", KEY).exchange())
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void aStaleUpdateLosesInsteadOfOverwriting() {
            UUID id = createSilverSpring();
            SavedLocation first = transactions.execute(status -> repository.findById(id).orElseThrow());
            SavedLocation second = transactions.execute(status -> repository.findById(id).orElseThrow());

            first.update("First writer", 1, 1, "UTC", T0);
            repository.save(first);
            second.update("Second writer", 2, 2, "UTC", T0);

            assertThatThrownBy(() -> repository.save(second)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
            assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("First writer");
        }
    }

    @Nested
    class Listing {

        @Test
        void pagesByNameThenId() {
            for (String name : new String[] {"Zion", "Alpha", "Mission"}) {
                assertThat(post("{\"name\":\"" + name + "\",\"latitude\":1,\"longitude\":1,\"timeZone\":\"UTC\"}", KEY))
                        .hasStatus(HttpStatus.CREATED);
            }

            MvcTestResult firstPage = mvc.get().uri("/api/v1/locations?size=2").exchange();
            assertThat(firstPage).bodyJson().extractingPath("$.content[*].name").asArray().containsExactly("Alpha", "Mission");
            assertThat(firstPage).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
            assertThat(firstPage).bodyJson().extractingPath("$.totalPages").isEqualTo(2);

            MvcTestResult secondPage = mvc.get().uri("/api/v1/locations?size=2&page=1").exchange();
            assertThat(secondPage).bodyJson().extractingPath("$.content[*].name").asArray().containsExactly("Zion");
        }

        @ParameterizedTest
        @CsvSource({"page=-1", "size=0", "size=101"})
        void rejectsBadPaging(String query) {
            assertThat(mvc.get().uri("/api/v1/locations?" + query).exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class SabbathForASavedLocation {

        @Test
        void usesTheSavedCoordinatesAndZone() {
            UUID id = createSilverSpring();

            MvcTestResult result = mvc.get().uri("/api/v1/locations/" + id + "/sabbath?date=2026-10-02").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.location.timeZone").isEqualTo("America/New_York");
            assertThat(result).bodyJson().extractingPath("$.sabbath.begins.time").asString().startsWith("2026-10-02T18:");
        }

        @Test
        void supportsUpcomingAndStatus() {
            UUID id = createSilverSpring();

            assertThat(mvc.get().uri("/api/v1/locations/" + id + "/sabbath/upcoming?count=2").exchange())
                    .bodyJson().extractingPath("$.sabbaths[*].friday").asArray().containsExactly("2026-10-02", "2026-10-09");
            assertThat(mvc.get().uri("/api/v1/locations/" + id + "/sabbath/status?at=2026-10-03T14:00:00Z").exchange())
                    .bodyJson().extractingPath("$.state").isEqualTo("IN_SABBATH");
        }

        @Test
        void returns404ForAnUnknownLocation() {
            assertThat(mvc.get().uri("/api/v1/locations/" + UUID.randomUUID() + "/sabbath").exchange())
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }
}
