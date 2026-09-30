package io.github.bdeeker.sabbathhours.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Reads are public. Writes need the {@code X-API-Key} header. There are no sessions, cookies,
 * or logins, so CSRF protection (which defends cookie-based sessions) does not apply and is off.
 * Browsers on any site may call the read endpoints (CORS), so a church website can show its
 * times with a few lines of JavaScript; writes are for server-to-server use and get no CORS.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiKeyProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyProperties apiKey) throws Exception {
        if (!apiKey.writesEnabled()) {
            log.warn("sabbath.api.write-key is not set: saved locations are read-only and every write will be refused.");
        }
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKey.writeKey()), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Let the container's own error and forward dispatches through, or a real error (say, a
                        // malformed URL on a POST) would be re-checked, denied, and reported as a misleading 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/locations").hasRole(ApiKeyAuthenticationFilter.WRITER_ROLE)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/locations/*").hasRole(ApiKeyAuthenticationFilter.WRITER_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/locations/*").hasRole(ApiKeyAuthenticationFilter.WRITER_ROLE)
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> writeProblem(response,
                                HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                                "This request needs a valid X-API-Key header."))
                        .accessDeniedHandler((request, response, ex) -> writeProblem(response,
                                HttpServletResponse.SC_FORBIDDEN, "Forbidden", "This request is not allowed.")));
        return http.build();
    }

    /** Any origin may read. No credentials are involved, so a wildcard origin is safe here. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration reads = new CorsConfiguration();
        reads.setAllowedOrigins(List.of("*"));
        reads.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
        reads.setAllowedHeaders(List.of("Accept", "Accept-Language", "Content-Type"));
        reads.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", reads);
        return source;
    }

    /** A fixed RFC 9457 body; nothing from the request is echoed into it. */
    private static void writeProblem(HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}
