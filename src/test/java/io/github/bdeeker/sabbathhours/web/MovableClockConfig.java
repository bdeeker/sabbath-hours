package io.github.bdeeker.sabbathhours.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the application clock with one each test can move. Import it explicitly where needed. */
@TestConfiguration(proxyBeanMethods = false)
public class MovableClockConfig {

    @Bean
    @Primary
    MovableClock testClock() {
        return new MovableClock(Instant.EPOCH);
    }

    public static final class MovableClock extends Clock {
        private volatile Instant instant;

        MovableClock(Instant instant) {
            this.instant = instant;
        }

        public void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }
    }
}
