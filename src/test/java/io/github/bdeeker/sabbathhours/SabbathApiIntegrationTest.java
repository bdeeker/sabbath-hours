package io.github.bdeeker.sabbathhours;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls the running application over real HTTP, so the actual JSON serializer, error handling,
 * and servlet container are all in play (unlike the MockMvc tests).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SabbathApiIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String pathAndQuery) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + pathAndQuery)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesTheSabbathAsJsonWithLocalOffsets() throws Exception {
        HttpResponse<String> response = get(
                "/api/v1/sabbath?latitude=39.0615&longitude=-76.9671&timeZone=America/New_York&date=2026-10-02");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("application/json"));
        // Times keep the location's own offset; they are not converted to UTC by the serializer.
        assertThat(response.body())
                .contains("\"begins\":{\"type\":\"SUNSET\",\"time\":\"2026-10-02T18:49:00-04:00\"}")
                .contains("\"friday\":\"2026-10-02\"");
    }

    @Test
    void keepsNullTimesExplicitForPolarWeeks() throws Exception {
        HttpResponse<String> response = get(
                "/api/v1/sabbath?latitude=69.6492&longitude=18.9553&timeZone=Europe/Oslo&date=2026-12-18");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"begins\":{\"type\":\"POLAR_NIGHT\",\"time\":null}");
    }

    @Test
    void returnsProblemDetailsForBadInput() throws Exception {
        HttpResponse<String> response = get("/api/v1/sabbath?latitude=91&longitude=0&timeZone=-04:00");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body())
                .contains("\"field\":\"latitude\"")
                .contains("\"field\":\"timeZone\"")
                .doesNotContain("Exception")
                .doesNotContain("at io.github");
    }

    @Test
    void keepsErrorMessagesInEnglishButTranslatesTheSummary() throws Exception {
        HttpRequest bad = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/api/v1/sabbath?latitude=91&longitude=0&timeZone=America/New_York"))
                .header("Accept-Language", "es").GET().build();
        assertThat(http.send(bad, HttpResponse.BodyHandlers.ofString()).body())
                .contains("must be less than or equal to 90.0");

        HttpRequest good = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/sabbath?latitude=39.0615&longitude=-76.9671&timeZone=America/New_York&date=2026-10-02"))
                .header("Accept-Language", "es").GET().build();
        assertThat(http.send(good, HttpResponse.BodyHandlers.ofString()).body())
                .contains("El sábado comienza el viernes 2 de octubre a las 18:49");
    }

    @Test
    void publishesAnAccurateOpenApiDescription() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode doc = JsonMapper.builder().build().readTree(response.body());
        JsonNode sabbathParams = doc.at("/paths/~1api~1v1~1sabbath/get/parameters");
        List<String> names = new ArrayList<>();
        sabbathParams.forEach(p -> names.add(p.get("name").asString()));
        // The LocationQuery record is flattened into real query parameters; the Locale argument is not a parameter.
        assertThat(names).containsExactlyInAnyOrder("latitude", "longitude", "timeZone", "date");

        assertThat(doc.at("/components/securitySchemes/writeApiKey/name").asString()).isEqualTo("X-API-Key");
        assertThat(doc.at("/paths/~1api~1v1~1locations/post/security").isMissingNode()).isFalse();
        assertThat(doc.at("/paths/~1api~1v1~1locations/get/security").isMissingNode()).isTrue();

        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    @Test
    void exposesHealthButNotOtherActuatorEndpoints() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/beans").statusCode()).isEqualTo(404);
    }
}
