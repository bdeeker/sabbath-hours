package io.github.bdeeker.sabbathhours.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** With no write key configured, the API is read-only and every write fails closed, whatever key is sent. */
@SpringBootTest(properties = "sabbath.api.write-key=")
@AutoConfigureMockMvc
class WritesDisabledTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void refusesWritesEvenWithAKeyHeader() {
        assertThat(mvc.post().uri("/api/v1/locations").header("X-API-Key", "anything-at-all-0123456789-abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"latitude\":1,\"longitude\":1,\"timeZone\":\"UTC\"}").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        // An empty header must not match an empty (unset) key.
        assertThat(mvc.post().uri("/api/v1/locations").header("X-API-Key", "")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"latitude\":1,\"longitude\":1,\"timeZone\":\"UTC\"}").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void stillServesReads() {
        assertThat(mvc.get().uri("/api/v1/locations").exchange()).hasStatusOk();
    }

    @Test
    void keyRulesAreEnforcedWhenTheConfigurationIsBound() {
        assertThat(new ApiKeyProperties(null).writesEnabled()).isFalse();
        assertThat(new ApiKeyProperties("   ").writesEnabled()).isFalse();
        assertThat(new ApiKeyProperties("x".repeat(32)).writesEnabled()).isTrue();
        assertThatThrownBy(() -> new ApiKeyProperties("x".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
        assertThat(new ApiKeyProperties("secret-".repeat(6)).toString()).doesNotContain("secret").contains("(set)");
    }
}
