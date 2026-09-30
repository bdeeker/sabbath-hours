package io.github.bdeeker.sabbathhours.config;

import java.util.Locale;

import jakarta.validation.MessageInterpolator;

import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration(proxyBeanMethods = false)
public class ValidationConfig {

    /**
     * Validation messages are always English. They are for developers calling the API, and the
     * handler's own messages ("is required") are English, so translating only the validator's
     * would mix two languages in one error response. Only the Sabbath summary is translated.
     *
     * <p>Static, like Spring Boot's own default validator, so it is ready before bean post-processors run.
     */
    @Bean
    public static LocalValidatorFactoryBean defaultValidator() {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setMessageInterpolator(new EnglishOnly(new ResourceBundleMessageInterpolator()));
        return factory;
    }

    /** Ignores the requested locale; Spring passes the request's locale through, which is what we must override. */
    private record EnglishOnly(MessageInterpolator delegate) implements MessageInterpolator {

        @Override
        public String interpolate(String messageTemplate, Context context) {
            return delegate.interpolate(messageTemplate, context, Locale.ENGLISH);
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            return delegate.interpolate(messageTemplate, context, Locale.ENGLISH);
        }
    }
}
