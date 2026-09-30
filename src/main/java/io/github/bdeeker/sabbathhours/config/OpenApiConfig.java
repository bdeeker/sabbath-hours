package io.github.bdeeker.sabbathhours.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bdeeker.sabbathhours.security.ApiKeyAuthenticationFilter;

/** OpenAPI description served at {@code /v3/api-docs}, with Swagger UI at {@code /swagger-ui.html}. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Name of the security scheme that write operations reference. */
    public static final String API_KEY_SCHEME = "writeApiKey";

    @Bean
    public OpenAPI sabbathHoursOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sabbath Hours API")
                        .version("v1")
                        .description("""
                                Sunset-based Sabbath times (Friday sunset to Saturday sunset) for any location.
                                Times are local to the location's IANA time zone and rounded to the minute.
                                Summaries follow Accept-Language (en, es, fr, pt). Reads are public;
                                writes to saved locations need the X-API-Key header.""")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ApiKeyAuthenticationFilter.HEADER)));
    }
}
