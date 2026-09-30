package io.github.bdeeker.sabbathhours.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * The value must be an IANA time zone ID such as {@code America/New_York}. Raw UTC offsets
 * ({@code -04:00}, {@code UTC+5}) are rejected on purpose: an offset cannot follow daylight
 * saving time, so it would give the wrong sunset for half the year. A null or blank value is
 * allowed; pair with {@code @NotBlank} to require one.
 */
@Documented
@Constraint(validatedBy = IanaTimeZone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IanaTimeZone {

    String message() default "must be an IANA time zone ID such as America/New_York, not a UTC offset";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<IanaTimeZone, String> {

        /** Offset-style IDs that ZoneId accepts but that are not real regions: UTC+5, GMT-03:00, UT+1. */
        private static final Pattern PREFIXED_OFFSET = Pattern.compile("^(UTC|GMT|UT)[+-].*");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // Missing or blank is @NotBlank's job; reporting both would give the caller two messages for one mistake.
            if (value == null || value.isBlank()) {
                return true;
            }
            return isIanaZone(value);
        }

        public static boolean isIanaZone(String value) {
            if (PREFIXED_OFFSET.matcher(value).matches()) {
                return false;
            }
            try {
                return !(ZoneId.of(value) instanceof ZoneOffset);
            } catch (DateTimeException e) {
                return false;
            }
        }
    }
}
