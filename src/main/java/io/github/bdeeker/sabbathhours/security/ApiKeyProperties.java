package io.github.bdeeker.sabbathhours.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code sabbath.api.write-key}, normally set with the {@code SABBATH_API_WRITE_KEY} environment variable.
 *
 * <p>Fails closed: with no key configured, every write is refused. A key that is set but shorter
 * than {@link #MIN_LENGTH} stops the application from starting, so a weak key can't go live by accident.
 */
@ConfigurationProperties(prefix = "sabbath.api")
public record ApiKeyProperties(String writeKey) {

    public static final int MIN_LENGTH = 32;

    public ApiKeyProperties {
        if (writeKey != null && writeKey.isBlank()) {
            writeKey = null;
        }
        if (writeKey != null && writeKey.length() < MIN_LENGTH) {
            throw new IllegalStateException(
                    "sabbath.api.write-key must be at least " + MIN_LENGTH + " characters (it is " + writeKey.length() + ")");
        }
    }

    public boolean writesEnabled() {
        return writeKey != null;
    }

    /** Never print the key itself. */
    @Override
    public String toString() {
        return "ApiKeyProperties[writeKey=" + (writesEnabled() ? "(set)" : "(not set)") + "]";
    }
}
