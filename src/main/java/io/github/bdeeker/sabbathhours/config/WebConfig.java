package io.github.bdeeker.sabbathhours.config;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration(proxyBeanMethods = false)
public class WebConfig {

    /** Languages with a messages_xx.properties bundle. Anything else gets English. */
    public static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.ENGLISH, Locale.of("es"), Locale.FRENCH, Locale.of("pt"));

    /** The source of "now" and "today". A bean so tests can pin it to a fixed moment. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Picks the summary language from the Accept-Language header. The bean name is what Spring MVC looks up. */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }
}
