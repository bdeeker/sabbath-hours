package io.github.bdeeker.sabbathhours;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Sabbath Hours API. {@link UserDetailsServiceAutoConfiguration} is excluded because there are no
 * user accounts: without the exclusion Spring Boot creates a default user and logs a generated
 * password, which would suggest a login exists when it doesn't.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SabbathHoursApplication {

    public static void main(String[] args) {
        SpringApplication.run(SabbathHoursApplication.class, args);
    }
}
