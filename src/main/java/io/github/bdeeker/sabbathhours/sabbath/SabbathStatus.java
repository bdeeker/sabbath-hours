package io.github.bdeeker.sabbathhours.sabbath;

import java.time.Instant;

/**
 * Whether the Sabbath is in progress at {@code at}.
 *
 * @param window the Sabbath in progress when {@code state} is {@code IN_SABBATH}; otherwise the
 *               Sabbath the question was about (the upcoming one, or the undetermined one)
 */
public record SabbathStatus(State state, Instant at, SabbathWindow window) {

    public enum State {
        IN_SABBATH,
        NOT_SABBATH,
        /** The answer depends on a sunset that does not happen (polar day or night). */
        UNDETERMINED
    }
}
